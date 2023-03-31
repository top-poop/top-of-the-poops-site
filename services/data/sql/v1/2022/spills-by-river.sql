with reports_last as (
    select company_name,
           receiving_water                     as river_name,
           coalesce(sum(spill_count), 0)       as total_count,
           coalesce(sum(total_spill_hours), 0) as total_hours
    from edm_consent_view
    where reporting_year = 2021 and rec_env_code_description in ( 'Canal', 'Estuary/Tidal River', 'Freshwater river')
    group by reporting_year, company_name, river_name
),
     reports_this as (
         select company_name,
                receiving_water                     as river_name,
                coalesce(sum(spill_count), 0)       as total_count,
                coalesce(sum(total_spill_hours), 0) as total_hours
         from edm_consent_view
         where reporting_year = 2022  and rec_env_code_description in ( 'Canal', 'Estuary/Tidal River', 'Freshwater river')
         group by reporting_year, company_name, river_name
     )
select reports_this.*,
       coalesce(reports_this.total_count - reports_last.total_count,reports_this.total_count) as spills_increase,
       coalesce(reports_this.total_hours - reports_last.total_hours,reports_this.total_hours) as hours_increase
from reports_this
         left join reports_last on reports_this.company_name = reports_last.company_name and
                                   reports_this.river_name = reports_last.river_name
where
   reports_last.total_hours > 24 or reports_this.total_hours > 24
order by total_hours desc, river_name
