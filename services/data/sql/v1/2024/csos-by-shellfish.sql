select reporting_year,
       company_name,
       site_name,
       shellfishery,
       total_spill_hours,
       spill_count,
       coalesce(reporting_pct, 0) * 100 as reporting_pct,
       receiving_water,
       lat,
       lon,
       pcon24nm
from edm_consent_view as edm
         join grid_references as grid on edm.effluent_grid_ref = grid.grid_reference
where shellfishery is not null
  and reporting_year = 2024
  and total_spill_hours > 0
order by total_spill_hours desc
;