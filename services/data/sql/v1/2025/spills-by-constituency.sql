WITH reports_last AS (SELECT pcon24nm                                AS constituency,
                             COALESCE(SUM(edm.spill_count), 0)       AS total_spills,
                             COALESCE(SUM(edm.total_spill_hours), 0) AS total_hours,
                             count(*)                                as cso_count
                      FROM edm_consent_view edm
                               JOIN grid_references ON edm.effluent_grid_ref = grid_references.grid_reference
                      WHERE reporting_year = 2024
                        AND pcon24nm IS NOT NULL
                      GROUP BY pcon24nm),
     reports_this AS (SELECT pcon24nm                                AS constituency,
                             COALESCE(SUM(edm.spill_count), 0)       AS total_spills,
                             COALESCE(SUM(edm.total_spill_hours), 0) AS total_hours,
                             count(*)                                as cso_count
                      FROM edm_consent_view edm
                               JOIN grid_references ON edm.effluent_grid_ref = grid_references.grid_reference
                      WHERE reporting_year = 2025
                        AND pcon24nm IS NOT NULL
                      GROUP BY pcon24nm)
SELECT c.pcon24nm                                             as constituency,
       coalesce(r_this.cso_count, 0)                          as cso_count,
       COALESCE(r_this.total_spills, 0)                       AS total_spills,
       COALESCE(r_this.total_hours, 0)                        AS total_hours,
       COALESCE(r_this.total_spills - r_last.total_spills, 0) AS spills_increase,
       COALESCE(r_this.total_hours - r_last.total_hours, 0)   AS hours_increase,
       concat(mps.first_name, ' ', mps.last_name)             AS mp_name,
       mps.party                                              AS mp_party,
       mps.uri                                                AS mp_uri,
       mps_twitter.screen_name                                AS twitter_handle

FROM pcon_july_2024_uk_bfc c
         LEFT JOIN reports_this r_this ON c.pcon24nm = r_this.constituency
         LEFT JOIN reports_last r_last ON c.pcon24nm = r_last.constituency
         LEFT JOIN mps ON c.pcon24nm = mps.constituency
         LEFT JOIN mps_twitter ON mps.constituency = mps_twitter.constituency
ORDER BY total_hours DESC, cso_count desc;
