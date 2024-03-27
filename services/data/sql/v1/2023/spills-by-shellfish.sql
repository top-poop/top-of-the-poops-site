with reports_last as (
    select company_name,
           shellfishery,
           coalesce(sum(spill_count), 0)       as total_count,
           coalesce(sum(total_spill_hours), 0) as total_spill_hours,
           avg(reporting_pct)                  as mean_reporting_pct,
           st_x(st_centroid(st_collect(point))) as lon,
           st_y(st_centroid(st_collect(point))) as lat
    from edm_consent_view edm
             join grid_references as grid on edm.effluent_grid_ref = grid.grid_reference
    where shellfishery is not null
      and reporting_year = 2022
    group by company_name, shellfishery
),
     reports_this as (
         select company_name,
                shellfishery,
                coalesce(sum(spill_count), 0)       as total_count,
                coalesce(sum(total_spill_hours), 0) as total_spill_hours,
                avg(reporting_pct)                  as mean_reporting_pct,
                st_x(st_centroid(st_collect(point))) as lon,
                st_y(st_centroid(st_collect(point))) as lat
         from edm_consent_view edm
                  join grid_references as grid on edm.effluent_grid_ref = grid.grid_reference
         where shellfishery is not null
           and reporting_year = 2023
         group by company_name, shellfishery
     )
select reports_this.*,
       coalesce(reports_this.total_count - reports_last.total_count, reports_this.total_count) as spills_increase,
       coalesce(reports_this.total_spill_hours - reports_last.total_spill_hours, reports_this.total_spill_hours) as hours_increase
from reports_this
         left join reports_last on reports_this.company_name = reports_last.company_name and
                                   reports_this.shellfishery = reports_last.shellfishery
order by total_spill_hours desc
