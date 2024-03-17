package org.totp.db

import org.totp.model.data.ConstituencyName

class EDM(private val connection: WithConnection) {


    data class ConstituencyAnnualSummary(
        val constituency: ConstituencyName,
        val year: Int,
        val hours: Double,
        val spills: Double
    )

    fun annualSummariesForConstituency(
        constituencyName: ConstituencyName,
    ): List<ConstituencyAnnualSummary> {
        return connection.execute(NamedQueryBlock.block("constituency-rainfall") {
            query(
                sql = """
    select reporting_year,
           pcon20nm                   as constituency,
           sum(edm.spill_count)       as total_spills,
           sum(edm.total_spill_hours) as total_hours
    from edm_consent_view edm
             join grid_references on edm.effluent_grid_ref = grid_references.grid_reference
    where pcon20nm = ?
    group by reporting_year, pcon20nm
    order by pcon20nm, reporting_year
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