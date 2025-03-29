select reporting_year,
       company_name,
       site_name,
       bathing,
       total_spill_hours,
       spill_count,
       coalesce(reporting_pct, 0) * 100 as reporting_pct,
       receiving_water,
       lat,
       lon,
       pcon24nm,
       beach_name
from edm_consent_view edm
         join grid_references grid on edm.effluent_grid_ref = grid.grid_reference
         left join edm_bathing_to_beach_mapping bm on edm.bathing = bm.edm_name
where reporting_year = 2024
  and bathing is not null
  and pcon24nm is not null
/* todo - one cso for beach is not mapped to constituency properly */
order by company_name