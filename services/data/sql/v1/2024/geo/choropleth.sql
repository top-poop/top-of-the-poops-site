with reports_last as (select pcon24nm                                as constituency,
                             coalesce(sum(edm.spill_count), 0)       as total_spills,
                             coalesce(sum(edm.total_spill_hours), 0) as total_hours,
                             count(*)                                as cso_count
                      from edm_consent_view edm
                               join grid_references on edm.effluent_grid_ref = grid_references.grid_reference
                      where reporting_year = 2023
                        and pcon24nm is not null
                      group by reporting_year, pcon24nm),
     reports_this as (select pcon24nm                                as constituency,
                             coalesce(sum(edm.spill_count), 0)       as total_spills,
                             coalesce(sum(edm.total_spill_hours), 0) as total_hours,
                             count(*)                                as cso_count
                      from edm_consent_view edm
                               join grid_references on edm.effluent_grid_ref = grid_references.grid_reference
                      where reporting_year = 2024
                        and pcon24nm is not null
                      group by reporting_year, pcon24nm),
     wanted as (select con.pcon24nm                                                                       as constituency,
                       coalesce(reports_this.total_spills, 0)                                             as total_spills,
                       coalesce(reports_this.total_hours, 0)                                              as total_hours,
                       coalesce(reports_this.cso_count, 0)                                                as cso_count,
                       coalesce(reports_this.total_spills - reports_last.total_spills, 0)                 as spills_increase,
                       coalesce(reports_this.total_hours - reports_last.total_hours, 0)                   as hours_increase,
                       concat(mps.first_name, ' ', mps.last_name)                                         as mp_name,
                       mps.party                                                                          as mp_party,
                       mps.uri                                                                            as mp_uri,
                       mps_twitter.screen_name                                                            as twitter_handle,
                       st_asgeojson(st_forcepolygoncw(st_simplifypreservetopology(wkb_geometry, 0.005))) as geometry
                from pcon_july_2024_uk_bfc con
                         left join reports_this on reports_this.constituency = con.pcon24nm
                         left join reports_last on reports_this.constituency = reports_last.constituency
                         left join mps on reports_this.constituency = mps.constituency
                         left join mps_twitter on mps.constituency = mps_twitter.constituency
                where con.pcon24cd like 'E%'
                   or con.pcon24cd like 'W%' or con.pcon24cd like 'S%'
                order by constituency)
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



