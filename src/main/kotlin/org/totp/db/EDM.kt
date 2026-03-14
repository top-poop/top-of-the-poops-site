package org.totp.db

import dev.forkhandles.values.StringValue
import org.totp.model.data.ConstituencyName
import org.totp.model.data.PlaceName
import org.totp.model.data.SeneddConstituencyName

class EDM(private val connection: WithConnection) {

    data class AnnualSummary(
        val place: StringValue,
        val year: Int,
        val hours: Double,
        val spills: Long,
        val csoCount: Int,
    )

    fun annualSummariesForLocality(
        placeName: PlaceName,
    ): List<AnnualSummary> {
        return connection.execute(NamedQueryBlock.block("locality-annual-summary") {
            query(
                sql = """
select reporting_year,
       a.name1_text               as locality,
       sum(edm.spill_count)       as total_spills,
       sum(edm.total_spill_hours) as total_hours,
       count(*)                   as cso_count
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
                    it.set(1, placeName)
                },
                mapper = {
                    AnnualSummary(
                        place = placeName,
                        year = it.getInt("reporting_year"),
                        hours = it.getDouble("total_hours"),
                        spills = it.getLong("total_spills"),
                        csoCount = it.getInt("cso_count")
                    )
                }
            )
        })
    }

    fun annualSummariesForSeneddConstituency(
        constituencyName: SeneddConstituencyName,
    ): List<AnnualSummary> {
        return connection.execute(NamedQueryBlock.block("constituency-annual-summary") {
            query(
                sql = """
WITH reporting_years AS (
    SELECT DISTINCT reporting_year
    FROM edm_consent_view
)
SELECT ry.reporting_year,
       s.english_na                              AS constituency,
       COUNT(edm.effluent_grid_ref)            AS cso_count,
       COALESCE(SUM(edm.spill_count), 0)       AS total_spills,
       COALESCE(SUM(edm.total_spill_hours), 0) AS total_hours
FROM senedd_final_2026 s
         CROSS JOIN reporting_years ry
         JOIN senedd_cons sc on ( s.ogc_fid = sc.ogc_fid)
         LEFT JOIN grid_references gr
                   ON gr.pcon24nm = sc.pcon24nm
         LEFT JOIN edm_consent_view edm
                   ON edm.reporting_year = ry.reporting_year
                       AND edm.effluent_grid_ref = gr.grid_reference
WHERE s.english_na = ?
GROUP BY ry.reporting_year, s.english_na
ORDER BY s.english_na, ry.reporting_year """.trimIndent(),
                bind = {
                    it.set(1, constituencyName.value)
                },
                mapper = {
                    AnnualSummary(
                        place = constituencyName,
                        year = it.getInt("reporting_year"),
                        hours = it.getDouble("total_hours"),
                        spills = it.getLong("total_spills"),
                        csoCount = it.getInt("cso_count")
                    )
                }
            )
        })
    }


    fun annualSummariesForConstituency(
        constituencyName: ConstituencyName,
    ): List<AnnualSummary> {
        return connection.execute(NamedQueryBlock.block("constituency-annual-summary") {
            query(
                sql = """
WITH reporting_years AS (
    SELECT DISTINCT reporting_year
    FROM edm_consent_view
)
SELECT ry.reporting_year,
       c.pcon24nm                              AS constituency,
       COUNT(edm.effluent_grid_ref)            AS cso_count,
       COALESCE(SUM(edm.spill_count), 0)       AS total_spills,
       COALESCE(SUM(edm.total_spill_hours), 0) AS total_hours
FROM pcon_july_2024_uk_bfc c
         CROSS JOIN reporting_years ry
         LEFT JOIN grid_references gr
                   ON gr.pcon24nm = c.pcon24nm
         LEFT JOIN edm_consent_view edm
                   ON edm.reporting_year = ry.reporting_year
                       AND edm.effluent_grid_ref = gr.grid_reference
WHERE c.pcon24nm = ?
GROUP BY ry.reporting_year, c.pcon24nm
ORDER BY c.pcon24nm, ry.reporting_year          
 """.trimIndent(),
                bind = {
                    it.set(1, constituencyName.value)
                },
                mapper = {
                    AnnualSummary(
                        place = constituencyName,
                        year = it.getInt("reporting_year"),
                        hours = it.getDouble("total_hours"),
                        spills = it.getLong("total_spills"),
                        csoCount = it.getInt("cso_count")
                    )
                }
            )
        })
    }
}