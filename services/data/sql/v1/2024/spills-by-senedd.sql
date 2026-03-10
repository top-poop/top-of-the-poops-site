WITH reports_last AS (SELECT english_na                                AS senedd,
                             COALESCE(SUM(edm.spill_count), 0)       AS total_spills,
                             COALESCE(SUM(edm.total_spill_hours), 0) AS total_hours,
                             count(*)                                as cso_count
                      FROM edm_consent_view edm
                               JOIN grid_references ON edm.effluent_grid_ref = grid_references.grid_reference
                               JOIN senedd_cons on grid_references.pcon24nm = senedd_cons.pcon24nm
                               JOIN senedd_final_2026 on senedd_cons.ogc_fid = senedd_final_2026.ogc_fid
                      WHERE reporting_year = 2023
                      GROUP BY english_na),
     reports_this AS (SELECT english_na                                AS senedd,
                             COALESCE(SUM(edm.spill_count), 0)       AS total_spills,
                             COALESCE(SUM(edm.total_spill_hours), 0) AS total_hours,
                             count(*)                                as cso_count
                      FROM edm_consent_view edm
                               JOIN grid_references ON edm.effluent_grid_ref = grid_references.grid_reference
                               JOIN senedd_cons on grid_references.pcon24nm = senedd_cons.pcon24nm
                               JOIN senedd_final_2026 on senedd_cons.ogc_fid = senedd_final_2026.ogc_fid
                      WHERE reporting_year = 2024
                      GROUP BY english_na)
SELECT c.english_na                                           as senedd,
       coalesce(r_this.cso_count, 0)                          as cso_count,
       COALESCE(r_this.total_spills, 0)                       AS total_spills,
       COALESCE(r_this.total_hours, 0)                        AS total_hours,
       COALESCE(r_this.total_spills - r_last.total_spills, 0) AS spills_increase,
       COALESCE(r_this.total_hours - r_last.total_hours, 0)   AS hours_increase
FROM senedd_final_2026 c
         LEFT JOIN reports_this r_this ON c.english_na = r_this.senedd
         LEFT JOIN reports_last r_last ON c.english_na = r_last.senedd
ORDER BY total_hours DESC, cso_count desc;
