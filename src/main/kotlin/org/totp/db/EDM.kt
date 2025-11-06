package org.totp.db

import org.totp.model.data.ConstituencyName
import org.totp.model.data.LocalityName

class EDM(private val connection: WithConnection) {


    data class ConstituencyAnnualSummary(
        val constituency: ConstituencyName,
        val year: Int,
        val hours: Double,
        val spills: Double
    )

    data class LocalityAnnualSummary(
        val locality: LocalityName,
        val year: Int,
        val hours: Double,
        val spills: Double
    )

    fun annualSummariesForLocality(
        localityName: LocalityName,
    ): List<LocalityAnnualSummary> {
        return connection.execute(NamedQueryBlock.block("locality-annual-summary") {
            query(
                sql = """
select reporting_year,
       a.name1_text               as locality,
       sum(edm.spill_count)       as total_spills,
       sum(edm.total_spill_hours) as total_hours
from edm_consent_view edm
         join grid_references gr on edm.effluent_grid_ref = gr.grid_reference
         join os_open_built_up_areas a
              on ST_Contains(a.geometry, gr.point) OR
                 ST_DWithin(gr.point_geog::geography, a.geography::geography, 1000)
where name1_text = ?
group by reporting_year, name1_text
order by name1_text, reporting_year
                """.trimIndent(),
                bind = {
                    it.set(1, localityName)
                },
                mapper = {
                    LocalityAnnualSummary(
                        locality = localityName,
                        year = it.getInt("reporting_year"),
                        hours = it.getDouble("total_hours"),
                        spills = it.getDouble("total_spills"),
                    )
                }
            )
        })
    }


    fun annualSummariesForConstituency(
        constituencyName: ConstituencyName,
    ): List<ConstituencyAnnualSummary> {
        return connection.execute(NamedQueryBlock.block("constituency-annual-summary") {
            query(
                sql = """
    select reporting_year,
           pcon24nm                   as constituency,
           sum(edm.spill_count)       as total_spills,
           sum(edm.total_spill_hours) as total_hours
    from edm_consent_view edm
             join grid_references on edm.effluent_grid_ref = grid_references.grid_reference
    where pcon24nm = ?
    group by reporting_year, pcon24nm
    order by pcon24nm, reporting_year
                """.trimIndent(),
                bind = {
                    it.set(1, constituencyName)
                },
                mapper = {
                    ConstituencyAnnualSummary(
                        constituency = constituencyName,
                        year = it.getInt("reporting_year"),
                        hours = it.getDouble("total_hours"),
                        spills = it.getDouble("total_spills"),
                    )
                }
            )
        })
    }
}