select date,
       count(*) as edm_count,
       count(case when overflowing > interval '30m' then 1 end) as overflowing,
       count(case when offline > interval '30m' then 1 end) as offline
from summary_thames
group by date
order by date
