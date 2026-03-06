package org.totp.search

import org.http4k.core.Uri
import org.http4k.events.Event
import org.http4k.events.Events
import org.totp.db.*
import org.totp.model.data.*
import org.totp.pages.toRenderable
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

enum class SearchResultType {
    Constituency, Place, Overflow, WaterCompany, River
}

data class WeightedSearchResult(
    val name: String,
    val text: String,
    val uri: Uri,
    val type: SearchResultType,
    val weight: Double
)

data class SearchEvent(val term: String) : Event

class Searcher(
    private val events: Events,
    private val connection: WithConnection,
    private val executor: ExecutorService,
    val rivers: () -> List<RiverRank>
) {

    private fun searchConstituencies(query: String): List<WeightedSearchResult> {
        return connection.execute(NamedQueryBlock("search-constituencies") {
            query(
                sql = """
select pcon24nm, pcon24nmw, st_area(wkb_geometry) as area from pcon_july_2024_uk_bfc
where (pcon24nm ilike ? or unaccent(pcon24nmw) ilike unaccent(?))
and (pcon24cd like 'E%' or pcon24cd like 'W%' or pcon24cd like 'S%')
order by st_area(wkb_geometry)  desc
limit 50
                """.trimIndent(),
                bind = {
                    it.set(1, "%${query}%")
                    it.set(2, "%${query}%")
                },
                mapper = {

                    val english = it.getString("pcon24nm")
                    val welsh = it.getString("pcon24nmw")

                    val text = if (english == welsh) {
                        english
                    } else {
                        listOfNotNull(english, welsh).joinToString(" / ")
                    }

                    WeightedSearchResult(
                        name = english,
                        text = text,
                        uri = ConstituencyName.of(english).toRenderable().uri,
                        type = SearchResultType.Constituency,
                        weight = it.getDouble("area")
                    )
                }
            )
        })
    }

    private fun searchPlaces(query: String): List<WeightedSearchResult> {
        return connection.execute(NamedQueryBlock("search-places") {
            query(
                sql = """
select name1_text, name1_language, name2_text, name2_language, areahectares from os_open_built_up_areas a
where areahectares > 100
and (unaccent(name1_text) ilike unaccent(?) or unaccent(name2_text) ilike unaccent(?))
order by areahectares desc
limit 50
                """.trimIndent(),
                bind = {
                    it.set(1, "%${query}%")
                    it.set(2, "%${query}%")
                },
                mapper = {
                    val name = it.getString("name1_text")
                    WeightedSearchResult(
                        name = name,
                        text = name,
                        uri = PlaceName.of(name).toRenderable().uri,
                        type = SearchResultType.Place,
                        weight = it.getDouble("areahectares")
                    )
                }
            )
        })
    }

    private fun searchOverflows(query: String): List<WeightedSearchResult> {
        return connection.execute(NamedQueryBlock("search-overflows") {
            query(
                sql = """
select m.stream_id, stream_company, site_name_wasc, site_name_consent, receiving_water, first_seen
from stream_cso m
           LEFT JOIN stream_lookup sl
                   ON (m.stream_id = sl.stream_id OR m.stream_id = sl.stream_id_old)
where (m.stream_id ilike ? or site_name_wasc ilike ? or receiving_water ilike ?)
limit 50
                """.trimIndent(),
                bind = {
                    it.set(1, "%${query}%")
                    it.set(2, "%${query}%")
                    it.set(3, "%${query}%")
                },
                mapper = {

                    val stream_id = it.getString("stream_id")
                    val company = StreamCompanyName(it.getString("stream_company")).asCompanyName()?.value ?: "Unknown"
                    val wasc: String? = it.getString("site_name_wasc")
                    val consent: String? = it.getString("site_name_consent")
                    val water: String? = it.getString("receiving_water")

                    val sitename = listOfNotNull(wasc, consent).firstOrNull() ?: "Unknown"

                    val text = listOfNotNull(stream_id.take(10), company, sitename, water).joinToString(" / ")

                    WeightedSearchResult(
                        name = stream_id,
                        text = text,
                        uri = StreamId.of(stream_id).toRenderable().uri,
                        type = SearchResultType.Overflow,
                        weight = it.getTimestamp("first_seen").toInstant().epochSecond.toDouble()
                    )
                }
            )
        })
    }

    private fun searchCompanies(query: String): List<WeightedSearchResult> {
        return Companies.entries
            .filter { it.companyName.value.lowercase().contains(query.lowercase()) }
            .map {
                WeightedSearchResult(
                    name = it.companyName.value,
                    text = it.companyName.value,
                    uri = it.companyName.toRenderable().uri,
                    type = SearchResultType.WaterCompany,
                    weight = 0.0,
                )
            }
    }

    private fun searchRivers(query: String): List<WeightedSearchResult> {
        val allRivers = rivers()

        return allRivers.filter { it.river.value.contains(query, ignoreCase = true) }
            .map {
                WeightedSearchResult(
                    name = it.company.value,
                    text = "${it.river} / ${it.company}",
                    it.river.toRenderable(it.company).uri,
                    type = SearchResultType.River,
                    weight = it.duration.seconds.toDouble()
                )
            }
    }

    fun search(query: String): List<WeightedSearchResult> {

        if (query.isBlank()) {
            return listOf()
        }

        events(SearchEvent(query))

        val tasks = listOf(
            Callable { searchConstituencies(query) },
            Callable { searchPlaces(query) },
            Callable { searchOverflows(query) },
            Callable { searchCompanies(query) },
            Callable { searchRivers(query) }
        )

        val futures = executor.invokeAll(tasks, 2, TimeUnit.SECONDS)

        val resultsUnordered = futures.flatMap { future -> if (future.isCancelled) emptyList() else future.get() }

        return resultsUnordered.sortedWith(compareBy<WeightedSearchResult> { when(it.type) {
            SearchResultType.WaterCompany -> 0
            SearchResultType.Constituency -> 1
            SearchResultType.Place -> 2
            SearchResultType.River -> 3
            SearchResultType.Overflow -> 4
        } }.thenDescending (compareBy { it.weight }))
    }
}