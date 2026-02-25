with reports_last as (
    select company_name,
           receiving_water                     as river_name,
           coalesce(sum(spill_count), 0)       as total_count,
           coalesce(sum(total_spill_hours), 0) as total_hours
    from edm_consent_view
    where reporting_year = 2023 and rec_env_code_description in ( 'Canal', 'Estuary/Tidal River', 'Freshwater river')
    group by reporting_year, company_name, river_name
),
     reports_this as (
         SELECT
             e.company_name,
             e.receiving_water AS river_name,
             COALESCE(SUM(e.spill_count), 0) AS total_count,
             COALESCE(SUM(e.total_spill_hours), 0) AS total_hours,
             ST_XMin(ST_Extent(gr.point::geometry)) AS min_lon,
             ST_YMin(ST_Extent(gr.point::geometry)) AS min_lat,
             ST_XMax(ST_Extent(gr.point::geometry)) AS max_lon,
             ST_YMax(ST_Extent(gr.point::geometry)) AS max_lat,
             ST_AsGeoJSON(
                     ST_MakeLine(gr.point::geometry ORDER BY
                         -- Morton/Z-order using scaled lat/lon
                         ((gr.lat + 90)::int << 16) | ((gr.lon + 180)::int & 65535)
                     )
             ) AS line_geojson
         FROM edm_consent_view e
                  LEFT JOIN grid_references gr
                            ON e.effluent_grid_ref = gr.grid_reference
         WHERE e.reporting_year = 2024
           AND e.rec_env_code_description IN ('Canal', 'Estuary/Tidal River', 'Freshwater river')
         GROUP BY e.company_name, e.receiving_water
     )
select reports_this.*,
       coalesce(reports_this.total_count - reports_last.total_count,reports_this.total_count) as spills_increase,
       coalesce(reports_this.total_hours - reports_last.total_hours,reports_this.total_hours) as hours_increase
from reports_this
         left join reports_last on reports_this.company_name = reports_last.company_name and
                                   reports_this.river_name = reports_last.river_name
order by total_hours desc, river_name
