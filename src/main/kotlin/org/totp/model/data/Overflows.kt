package org.totp.model.data

import com.fasterxml.jackson.databind.ObjectMapper
import dev.forkhandles.values.StringValue
import dev.forkhandles.values.StringValueFactory
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri
import org.totp.pages.ConstituencyRank
import org.totp.pages.ConstituencySlug
import org.totp.pages.MP
import java.time.Duration

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

            objectMapper.readerForListOf(HashMap::class.java)
                .readValue<List<Map<String, Any?>>>(response.bodyString())
                .mapIndexed { r, it ->
                    val constituencyName = ConstituencyName(it["constituency"] as String)
                    ConstituencyRank(
                        rank = r + 1,
                        constituencyName = constituencyName,
                        constituencyUri = Uri.of("/constituency/${ ConstituencySlug.from(constituencyName).value }"),
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

object ConstituencyCSOs {
    operator fun invoke(handler: HttpHandler): (ConstituencyName) -> List<CSOTotals> {
        return { name ->

            val response = handler(Request(Method.GET, "spills-all.json"))

            val list =
                objectMapper.readerForListOf(HashMap::class.java)
                    .readValue<List<Map<String, Any>>>(response.bodyString())
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