select this.*,
       last.total_spill_hours as last_hours,
       last.total_spill_count as last_count,
       coalesce(this.total_spill_count - last.total_spill_count,0) as spills_increase,
       coalesce(this.total_spill_hours - last.total_spill_hours,0) as hours_increase
from bathing_view this
         left join bathing_view as last on upper(this.bathing) = upper(last.bathing) and this.company_name = last.company_name and last.reporting_year = 2023
where
    this.reporting_year = 2024
order by this.total_spill_hours desc