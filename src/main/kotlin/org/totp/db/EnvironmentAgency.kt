package org.totp.db

import org.totp.model.data.ConstituencyName
import java.time.Instant
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.min

class EnvironmentAgency(private val connection: WithConnection) {

    data class RainfallGrid(
        val xmin: Double,
        val xmax: Double,
        val ymin: Double,
        val ymax: Double,
        val rain: Double,
        val count: Int
    )

    // instant at 15 min intervals...
    fun rainfallGridAt(instant: Instant): List<RainfallGrid> {
        return connection.execute(NamedQueryBlock.block("rainfall-grid") {
            query(
                sql = """
select count(*) as count,
       min(reading_mm),
       max(reading_mm),
       percentile_cont(0.75) within group ( order by reading_mm ) as p75,
       avg(reading_mm),
       st_xmin(st_extent(geom)) as xmin,
       st_xmax(st_extent(geom)) as xmax,
       st_ymin(st_extent(geom)) as ymin,
       st_ymax(st_extent(geom)) as ymax,
       geom
from rainfall_station_grid rs,
     rainfall_readings rr
where rs.station_id = rr.station_id
  and rr.date_time = ?
group by geom
                """.trimIndent(),
                bind = {
                    it.set(1, instant)
                },
                mapper = {
                    RainfallGrid(
                        xmin = it.getDouble("xmin"),
                        xmax = it.getDouble("xmax"),
                        ymin = it.getDouble("ymin"),
                        ymax = it.getDouble("ymax"),
                        rain = it.getDouble("p75"),
                        count = it.getInt("count")
                    )
                })
        })
    }


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
                     where pcon24nm = ? and date >= ? and date <= ?
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
}