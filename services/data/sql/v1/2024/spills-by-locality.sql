with reports_last as (select count(*)                                as cso_count,
                             name1_text                              as locality,
                             coalesce(sum(edm.spill_count), 0)       as total_spills,
                             coalesce(sum(edm.total_spill_hours), 0) as total_hours
                      from edm_consent_view edm
                               join grid_references gr on edm.effluent_grid_ref = gr.grid_reference
                               join os_open_built_up_areas a
                                    on ST_Contains(a.geometry, gr.point) OR
                                       ST_DWithin(gr.point::geography, a.geography::geography, 1000)
                      where reporting_year = 2023
                        and areahectares > 100
                      group by reporting_year, name1_text),
     reports_this as (select count(*)                                as cso_count,
                             name1_text                              as locality,
                             coalesce(sum(edm.spill_count), 0)       as total_spills,
                             coalesce(sum(edm.total_spill_hours), 0) as total_hours
                      from edm_consent_view edm
                               join grid_references gr on edm.effluent_grid_ref = gr.grid_reference
                               join os_open_built_up_areas a
                                    on ST_Contains(a.geometry, gr.point) OR
                                       ST_DWithin(gr.point::geography, a.geography::geography, 1000)
                      where reporting_year = 2024
                        and areahectares > 100
                      group by reporting_year, name1_text)

select reports_this.*,
       coalesce(reports_this.total_spills - reports_last.total_spills, 0) as spills_increase,
       coalesce(reports_this.total_hours - reports_last.total_hours, 0)   as hours_increase
from reports_this
         left join reports_last on reports_this.locality = reports_last.locality
order by total_hours desc, locality;
