select reporting_year,
       edm.company_name,
       sum(total_spill_hours) as hours,
       sum(spill_count)       as count,
       count(*)               as location_count
from edm
where total_spill_hours > 0
   or spill_count > 0
group by reporting_year, edm.company_name
order by edm.company_name, reporting_year;