with
     reports_this as (select english_na                                as english_na,
                             coalesce(sum(edm.spill_count), 0)       as total_spills,
                             coalesce(sum(edm.total_spill_hours), 0) as total_hours,
                             count(*)                                as cso_count
                      from edm_consent_view edm
                               join grid_references gr on edm.effluent_grid_ref = gr.grid_reference
                               join senedd_cons sc on sc.pcon24nm = gr.pcon24nm
                               join senedd_final_2026 s on s.ogc_fid = sc.ogc_fid
                      where reporting_year = 2025
                      group by reporting_year, english_na),
     wanted as (select s.english_na                                                                       as senedd,
                       coalesce(reports_this.total_spills, 0)                                             as total_spills,
                       coalesce(reports_this.total_hours, 0)                                              as total_hours,
                       coalesce(reports_this.cso_count, 0)                                                as cso_count,
                       st_asgeojson(st_forcepolygoncw(st_simplifypreservetopology(wkb_geometry, 0.005))) as geometry
                from senedd_final_2026 s
                         left join reports_this on reports_this.english_na = s.english_na
                order by senedd)
select jsonb_build_object(
               'type', 'FeatureCollection',
               'features', jsonb_agg(features.feature)
           )::text as geojson
from (select jsonb_build_object(
                     'type', 'Feature',
                     'properties', to_jsonb(things) - 'geometry',
                     'geometry', geometry::jsonb
                 ) as feature
      from (select * from wanted) things) features;



