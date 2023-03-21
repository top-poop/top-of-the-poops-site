package org.totp.model.data

import com.fasterxml.jackson.databind.ObjectMapper
import dev.forkhandles.values.StringValue
import dev.forkhandles.values.StringValueFactory
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri
import org.totp.extensions.kebabCase
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
            val uri = Uri.of("${name.value.kebabCase()}.json")
            GeoJSON(handler(Request(Method.GET, uri)).bodyString())
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
                            constituency = ConstituencyName(it["constituency"].toString()),
                            cso = CSO(
                                company = it["company_name"].toString(),
                                sitename = it["site_name"].toString(),
                                waterway = it["receiving_water"].toString(),
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