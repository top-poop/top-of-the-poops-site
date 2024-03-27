with reports_last as (
    select pcon20nm                                as constituency,
           coalesce(sum(edm.spill_count), 0)       as total_spills,
           coalesce(sum(edm.total_spill_hours), 0) as total_hours
    from edm_consent_view edm
             join grid_references on edm.effluent_grid_ref = grid_references.grid_reference
    where reporting_year = 2022
      and pcon20nm is not null
    group by reporting_year, pcon20nm
),
     reports_this as (
         select pcon20nm                                as constituency,
                coalesce(sum(edm.spill_count), 0)       as total_spills,
                coalesce(sum(edm.total_spill_hours), 0) as total_hours
         from edm_consent_view edm
                  join grid_references on edm.effluent_grid_ref = grid_references.grid_reference
         where reporting_year = 2023
           and pcon20nm is not null
         group by reporting_year, pcon20nm
     )
select reports_this.*,
       coalesce(reports_this.total_spills - reports_last.total_spills,0) as spills_increase,
       coalesce(reports_this.total_hours - reports_last.total_hours,0)   as hours_increase,
       concat(mps.first_name, ' ', mps.last_name)            as mp_name,
       mps.party                                             as mp_party,
       mps.uri                                               as mp_uri,
       mps_twitter.screen_name                               as twitter_handle
from reports_this
         left join reports_last on reports_this.constituency = reports_last.constituency
         left join mps on reports_this.constituency = mps.constituency
         left join mps_twitter on mps.constituency = mps_twitter.constituency
order by total_hours desc, constituency