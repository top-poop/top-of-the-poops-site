package org.totp.db

import dev.forkhandles.values.StringValue
import dev.forkhandles.values.StringValueFactory
import org.totp.model.data.ConstituencyName
import org.totp.model.data.GeoJSON
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.min

class EnvironmentAgency(private val connection: WithConnection) {


    //    "d": "2023-12-01",
//    "c": 0.2,
//    "r": "r-1",
//    "n": 2
    data class Rainfall(val d: LocalDate, val c: Double, val r: String, val n: Int)

    fun rainfallForConstituency(
        constituencyName: ConstituencyName,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Rainfall> {
        return connection.execute(NamedQueryBlock.block("constituency-rainfall") {
            query(
                sql = """
                     select date, min, avg, max, pct_75, count 
                     from rainfall_daily_consitituency 
                     where pcon20nm = ? and date >= ? and date <= ?
                """.trimIndent(),
                bind = {
                    it.set(1, constituencyName)
                    it.set(2, startDate)
                    it.set(3, endDate)
                },
                mapper = {
                    Rainfall(
                        d = it.getDate("date").toLocalDate(),
                        c = it.getDouble("pct_75"),
                        r = it.getDouble("pct_75").let { "r-${min(10.0, ceil(it / 2)).toInt()}" },
                        n = it.getInt("count")
                    )
                }
            )
        })
    }


    class WaterbodyId(value: String) : StringValue(value) {
        companion object : StringValueFactory<WaterbodyId>(::WaterbodyId)
    }
    class WaterbodyName(value: String) : StringValue(value) {
        // not the same as a waterwayname, one comes from EDM Consent, one from wfd
        companion object : StringValueFactory<WaterbodyName>(::WaterbodyName)
    }

    // serialised
    data class Waterbody(val id: WaterbodyId, val name: WaterbodyName, val geometry: GeoJSON)

    fun waterwayGeometry(waterbodyId: WaterbodyId): Waterbody? {
        return connection.execute(NamedQueryBlock.block("waterway-geometry") {
            query(
                sql = """
                    select
                    name,
                    st_asgeojson(st_forcepolygoncw(st_simplifypreservetopology(wkb_geometry, 0.0001))) as geometry
                    from rivers
                    where ea_wb_id = ?
                """.trimIndent(),
                bind = {
                       it.set(1, waterbodyId)
                },
                mapper = {
                    Waterbody(
                        waterbodyId,
                        it.get(WaterbodyName, "name"),
                        it.get(GeoJSON, "geometry")
                    )
                }
            )
        }).firstOrNull()
    }
}