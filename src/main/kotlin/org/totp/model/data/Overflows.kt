package org.totp.model.data

import com.fasterxml.jackson.databind.ObjectMapper
import dev.forkhandles.values.StringValue
import dev.forkhandles.values.StringValueFactory
import java.time.Duration
import kotlin.io.path.bufferedReader

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


fun csoSummaries(location: java.nio.file.Path): (ConstituencyName) -> List<CSOTotals> {
    val objectMapper = ObjectMapper()
    val list = location.bufferedReader().let {
        objectMapper.readerForListOf(HashMap::class.java).readValue<List<Map<String, Any>>>(it)
    }.map {
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

    return { name -> list.filter { it.constituency == name } }
}
