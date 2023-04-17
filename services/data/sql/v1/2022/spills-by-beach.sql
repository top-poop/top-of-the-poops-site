select this.*,
       coalesce(this.total_spill_count - last.total_spill_count,0) as spills_increase,
       coalesce(this.total_spill_hours - last.total_spill_hours,0) as hours_increase
from bathing_view this
         left join bathing_view as last on this.bathing = last.bathing and this.company_name = last.company_name
where
    this.reporting_year = 2022
and last.reporting_year = 2021
order by this.total_spill_hours desc