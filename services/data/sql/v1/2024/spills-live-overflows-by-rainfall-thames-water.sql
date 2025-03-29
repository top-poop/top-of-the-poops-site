select
    count(*) as count,
    floor(extract('epoch' from overflowing) /( 60 * 60))  as overflowing,
    ceil(rd.pct_75 / 1 ) * 1 as pct_75
from summary_thames st
         join consents_unique_view c on st.permit_id = c.permit_number
         join grid_references g on c.effluent_grid_ref = g.grid_reference
         join rainfall_daily_consitituency rd on g.pcon24nm =rd.pcon24nm and rd.date = st.date
group by floor(extract('epoch' from overflowing) /( 60 * 60)), ceil(rd.pct_75 / 1 ) * 1
order by count desc
