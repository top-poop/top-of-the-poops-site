with cons as (select *
              from edm_consent_view edm
                       join grid_references grid on edm.effluent_grid_ref = grid.grid_reference
              where reporting_year = 2024),
     locality_agg AS (
         SELECT
             gr.pcon24nm,
             gr.grid_reference,
             ARRAY_AGG(DISTINCT a.name1_text ORDER BY a.name1_text) AS localities
         FROM grid_references gr
                  JOIN os_open_built_up_areas a
                       ON ST_Contains(a.geometry, gr.point)
                           OR ST_DWithin(gr.point_geog::geography, a.geography::geography, 1000)
         GROUP BY gr.grid_reference
     ),
     agg as (select cons.pcon24nm                              as constituency,
                    company_name,
                    discharge_site_name                   as site_name,
                    receiving_water,
                    lat,
                    lon,
                    coalesce(sum(spill_count), 0)         as spill_count,
                    coalesce(sum(total_spill_hours), 0)   as total_spill_hours,
                    coalesce(avg(reporting_pct), 0) * 100 as reporting_percent,
                    coalesce(locality_agg.localities, '{}') as localities
             from cons
                      left join locality_agg on cons.grid_reference = locality_agg.grid_reference
             where cons.pcon24nm is not null
             group by cons.pcon24nm, company_name, discharge_site_name, receiving_water, lat, lon, localities)
select *
from agg
order by constituency, total_spill_hours desc, site_name
