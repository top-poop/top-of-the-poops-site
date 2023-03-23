select edm.bathing,
       company_name,
       sum(edm.total_spill_hours) as total_spill_hours,
       sum(edm.spill_count) as total_spill_count,
       st_x(st_centroid(st_collect(point))) as lon,
       st_y(st_centroid(st_collect(point))) as lat
from edm_consent_view as edm
         join grid_references as grid on edm.effluent_grid_ref = grid.grid_reference
where bathing is not null
  and reporting_year = 2021
  and total_spill_hours > 0
group by bathing, company_name
having sum(edm.total_spill_hours) > 10
order by total_spill_hours desc ;