package org.totp.db

import org.totp.db.NamedQueryBlock.Companion.block
import org.totp.model.data.ConstituencyName
import org.totp.model.data.WaterwayName
import java.sql.ResultSet
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class ThamesWater(private val connection: WithConnection) {


    fun haveLiveDataFor(): Set<ConstituencyName> {
        return connection.execute(block("have-live-data") {
            query(
                sql = """
     with permits as (select distinct(permit_id) from summary_thames)
             select
                 distinct(g.pcon24nm) as constituency
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

    data class CSOLiveOverflow(
        val started: Instant,
        val pcon24nm: ConstituencyName,
        val waterwayName: WaterwayName,
        val site_name: String,
        val permit_id: String,
    )

    fun overflowingRightNow(): List<CSOLiveOverflow> {
        return connection.execute(NamedQueryBlock("overflowing-right-now") {
            query(
                sql = """                
with overflowing as (
    SELECT * FROM (
                      SELECT *, ROW_NUMBER() OVER (PARTITION BY reference_consent_id ORDER BY date_time DESC) rn
                      FROM events_thames
                      join consent_map cm on permit_number = cm.consent_id
                  ) tmp WHERE rn = 1 and alert_type = 'Start'
)
select pcon24nm, discharge_site_name, receiving_water, st.* from overflowing as st
    join consents_unique_view c on st.reference_consent_id = c.permit_number
    join grid_references g on c.effluent_grid_ref = g.grid_reference
order by date_time
            """.trimIndent(),
                mapper = {
                    CSOLiveOverflow(
                        pcon24nm = it.get(ConstituencyName, "pcon24nm"),
                        site_name = it.getString("discharge_site_name"),
                        waterwayName = it.get(WaterwayName, "receiving_water"),
                        permit_id = it.getString("permit_number"),
                        started = it.getTimestamp("date_time").toInstant()
                    )
                }
            )
        })
    }

//    {
//        "p": "ALDERSHOT STW",
//        "cid": "CTCR.1974",
//        "d": "2023-12-03",
//        "a": "u-24"
//    },


    fun eventSummaryForConstituency(
        constituencyName: ConstituencyName,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Thing> {
        return connection.execute(NamedQueryBlock("event-summary-for") {
            query(
                sql = """
                    select
                        permit_id,
                        discharge_site_name,
                        st.date,
                        extract(epoch from online) as stop,
                        extract(epoch from offline) as offline,
                        extract(epoch from overflowing) as start,
                        extract(epoch from unknown) as unknown,
                        extract(epoch from potentially_overflowing) as potential_start
                    from summary_thames st
                             join consents_unique_view c on st.permit_id = c.permit_number
                             join grid_references g on c.effluent_grid_ref = g.grid_reference
                    where g.pcon24nm = ? and date >= ? and date <= ?
                    order by st.date
                """.trimIndent(),
                bind = {
                    it.set(1, constituencyName)
                    it.set(2, startDate)
                    it.set(3, endDate)
                },
                mapper = {
                    thing(it)
                }
            )
        })
    }

    fun eventSummaryForCSO(permit_id: String, startDate: LocalDate, endDate: LocalDate): List<Thing> {
        return connection.execute(NamedQueryBlock("event-summary-for") {
            query(
                sql = """
                    select
                        permit_id,
                        discharge_site_name,
                        st.date,
                        extract(epoch from online) as stop,
                        extract(epoch from offline) as offline,
                        extract(epoch from overflowing) as start,
                        extract(epoch from unknown) as unknown,
                        extract(epoch from potentially_overflowing) as potential_start
                    from summary_thames st
                             join consents_unique_view c on st.permit_id = c.permit_number
                    where permit_id = ? and date >= ? and date <= ?
                    order by st.date
                """.trimIndent(),
                bind = {
                    it.set(1, permit_id)
                    it.set(2, startDate)
                    it.set(3, endDate)
                },
                mapper = {
                    thing(it)
                }
            )
        })
    }

    private fun thing(it: ResultSet) = Thing(
        p = it.getString("discharge_site_name"),
        cid = it.getString("permit_id"),
        d = it.getDate("date").toLocalDate(),
        a = codeFrom(
            bucketFrom(it)
        )
    )

    data class CSOSummary(
        val permit_id: String,
        val site_name: String,
        val pcon24nm: ConstituencyName,
        val overflowing: Duration
    )

    fun worstCSOsInPeriod(startDate: LocalDate, endDate: LocalDate): List<CSOSummary> {
        return connection.execute(NamedQueryBlock("") {
            query(
                sql = """
select
    permit_id,
    discharge_site_name,
    g.pcon24nm,
    extract(epoch from justify_hours(sum(overflowing))) as overflowing
from summary_thames st
         join consents_unique_view c on st.permit_id = c.permit_number
         join grid_references g on c.effluent_grid_ref = g.grid_reference
where date >= ? and  date <= ?
group by permit_id, discharge_site_name, pcon24nm
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
                        pcon24nm = it.get(ConstituencyName, "pcon24nm"),
                        overflowing = Duration.ofSeconds(it.getLong("overflowing"))
                    )
                }
            )
        })
    }

}