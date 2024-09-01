package org.totp.db

import org.totp.model.data.ConstituencyName
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