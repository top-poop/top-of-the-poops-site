package org.totp.model.data

import com.fasterxml.jackson.databind.ObjectMapper
import dev.forkhandles.values.StringValue
import dev.forkhandles.values.StringValueFactory
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri
import org.totp.extensions.kebabCase
import org.totp.extensions.readSimpleList
import org.totp.pages.ConstituencyRank
import org.totp.pages.ConstituencySlug
import org.totp.pages.MP
import java.time.Duration
import java.time.LocalDate

data class CSOTotals(
    val constituency: ConstituencyName,
    val cso: CSO,
    val count: Int,
    val duration: Duration,
    val reporting: Number
)

data class CSO(val company: String, val sitename: String, val waterway: String, val location: Coordinates)

data class Coordinates(val lat: Number, val lon: Number)

class ConstituencyName(value: String) : StringValue(value) {
    companion object : StringValueFactory<ConstituencyName>(::ConstituencyName)
}

val objectMapper = ObjectMapper()

object ConstituencyBoundaries {
    operator fun invoke(handler: HttpHandler): (ConstituencyName) -> GeoJSON {
        return { name ->
            val slug = ConstituencySlug.from(name)
            val uri = Uri.of("$slug.json")
            GeoJSON(handler(Request(Method.GET, uri)).bodyString())
        }
    }
}

object ConstituencyRankings {
    operator fun invoke(handler: HttpHandler): () -> List<ConstituencyRank> {
        return {
            val response = handler(Request(Method.GET, "spills-by-constituency.json"))

            objectMapper.readSimpleList(response.bodyString())
                .mapIndexed { r, it ->
                    val constituencyName = ConstituencyName(it["constituency"] as String)
                    ConstituencyRank(
                        rank = r + 1,
                        constituencyName = constituencyName,
                        constituencyUri = Uri.of("/constituency/${ConstituencySlug.from(constituencyName).value}"),
                        mp = MP(
                            name = it["mp_name"] as String,
                            party = it["mp_party"] as String,
                            handle = it["twitter_handle"] as String?,
                            uri = Uri.of(it["mp_uri"] as String)
                        ),
                        company = it["company"] as String,
                        count = (it["total_spills"] as Double).toInt(),
                        duration = Duration.ofHours((it["total_hours"] as Double).toLong()),
                        countDelta = (it["spills_increase"] as Double).toInt(),
                        durationDelta = Duration.ofHours((it["hours_increase"] as Double).toLong())
                    )
                }
        }
    }
}


data class BeachRank(
    val rank: Int,
    val beach: String,
    val company: String,
    val count: Int,
    val duration: Duration,
)

object BeachRankings {
    operator fun invoke(handler: HttpHandler): () -> List<BeachRank> {
        return {
            val response = handler(Request(Method.GET, "spills-by-beach.json"))

            objectMapper.readSimpleList(response.bodyString())
                .mapIndexed { r, it ->
                    BeachRank(
                        rank = r + 1,
                        beach = it["bathing"] as String,
                        company = it["company_name"] as String,
                        duration = Duration.ofHours((it["total_spill_hours"] as Double).toLong()),
                        count = (it["total_spill_count"] as Double).toInt(),
                    )
                }
        }
    }
}

object ConstituencyCSOs {
    operator fun invoke(handler: HttpHandler): (ConstituencyName) -> List<CSOTotals> {
        return { name ->

            val response = handler(Request(Method.GET, "spills-all.json"))

            val list =
                objectMapper.readSimpleList(response.bodyString())
                    .map {
                        CSOTotals(
                            constituency = ConstituencyName(it["constituency"] as String),
                            cso = CSO(
                                company = it["company_name"] as String,
                                sitename = it["site_name"] as String,
                                waterway = it["receiving_water"] as String,
                                location = Coordinates(
                                    lat = it["lat"] as Double,
                                    lon = it["lon"] as Double
                                )
                            ),
                            count = (it["spill_count"] as Double).toInt(),
                            duration = Duration.ofHours((it["total_spill_hours"] as Double).toLong()),
                            reporting = it["reporting_percent"] as Double
                        )
                    }

            list.filter { it.constituency == name }
        }
    }
}

data class MediaAppearance(
    val title: String,
    val publication: String,
    val date: LocalDate,
    val uri: Uri,
    val imageUri: Uri
)

object MediaAppearances {
    operator fun invoke(handler: HttpHandler): () -> List<MediaAppearance> {
        return {
            val response = handler(Request(Method.GET, "media-appearances.json"))

            objectMapper.readSimpleList(response.bodyString())
                .map {
                    MediaAppearance(
                        title = it["title"] as String,
                        publication = it["where"] as String,
                        date = LocalDate.parse(it["date"] as String),
                        uri = Uri.of(it["href"] as String),
                        imageUri = Uri.of(it["image"] as String)
                    )
                }
        }
    }
}


data class Address(val line1: String, val line2: String, val line3: String?, val town: String, val postcode: String)
data class WaterCompany(val name: String, val address: Address, val phone: Uri, val uri: Uri, val imageUri: Uri, val handle: String?)

object WaterCompanies {
    operator fun invoke(handler: HttpHandler): () -> List<WaterCompany> {
        return {
            val response = handler(Request(Method.GET, "water-companies.json"))

            objectMapper.readSimpleList(response.bodyString())
                .map {
                    val name = it["name"] as String
                    WaterCompany(
                        name = name,
                        (it["address"] as Map<String, String?>).let {
                            Address(
                                line1 = it["line1"] as String,
                                line2 = it["line2"] as String,
                                line3 = it["line3"],
                                town = it["town"] as String,
                                postcode = it["postcode"] as String
                            )
                        },
                        phone = Uri.of(""),
                        uri = Uri.of(it["web"] as String),
                        Uri.of("/assets/images/logos/${name.kebabCase()}.png"),
                        handle = it["twitter"] as String?
                    )
                }
        }
    }
}

