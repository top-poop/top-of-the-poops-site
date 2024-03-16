package org.totp.db

import org.totp.model.data.ConstituencyName
import java.time.Duration
import java.time.LocalDate

class ThamesWater(private val connection: WithConnection) {

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