package org.totp.db

import org.totp.db.NamedQueryBlock.Companion.block
import org.totp.model.data.ConstituencyName
import java.time.Duration
import java.time.LocalDate

class ThamesWater(private val connection: WithConnection) {


    fun haveLiveDataFor(): Set<ConstituencyName> {
        return connection.execute(block("have-live-data") {
            query(
                sql = """
     with permits as (select distinct(permit_id) from summary_thames)
             select
                 distinct(g.pcon20nm) as constituency
             from permits
                      join consents_unique_view c on permit_id = c.permit_number
                      join grid_references g on c.effluent_grid_ref = g.grid_reference
                """.trimIndent(),
                mapper = {
                    it.get(ConstituencyName, "constituency")
                }
            )
        }).toSet()
    }

    data class DatedOverflow(
        val date: LocalDate,
        val edm_count: Int,
        val overflowing: Int,
        val overflowingSeconds: Int,
        val offline: Int
    )

    fun infrastructureSummary(): List<DatedOverflow> {
        return connection.execute(block("infrastructureSummary") {
            query(
                sql = """select date,
       count(*) as edm_count,
       extract(epoch from sum(overflowing)) as overflowingSeconds, 
       count(case when overflowing > interval '30m' then 1 end) as overflowing,
       count(case when offline > interval '30m' then 1 end) as offline
from summary_thames
group by date
order by date
""",
                mapper = {
                    DatedOverflow(
                        it.getDate("date").toLocalDate(),
                        it.getInt("edm_count"),
                        it.getInt("overflowing"),
                        it.getInt("overflowingSeconds"),
                        it.getInt("offline")
                    )
                }
            )
        })
    }


    data class CSOSummary(
        val permit_id: String,
        val site_name: String,
        val pcon20nm: ConstituencyName,
        val overflowing: Duration
    )

    fun worstCSOsInPeriod(startDate: LocalDate, endDate: LocalDate): List<CSOSummary> {
        return connection.execute(NamedQueryBlock("") {
            query(sql = """
select
    permit_id,
    discharge_site_name,
    g.pcon20nm,
    extract(epoch from justify_hours(sum(overflowing))) as overflowing
from summary_thames st
         join consents_unique_view c on st.permit_id = c.permit_number
         join grid_references g on c.effluent_grid_ref = g.grid_reference
where date >= ? and  date <= ?
group by permit_id, discharge_site_name, pcon20nm
order by overflowing desc
limit 100
""",
                bind = {
                    it.set(1, startDate)
                    it.set(2, endDate)
                },
                mapper = {
                    CSOSummary(
                        permit_id = it.getString("permit_id"),
                        site_name = it.getString("discharge_site_name"),
                        pcon20nm = it.get(ConstituencyName, "pcon20nm"),
                        overflowing = Duration.ofSeconds(it.getLong("overflowing"))
                    )
                }
            )
        })
    }

}