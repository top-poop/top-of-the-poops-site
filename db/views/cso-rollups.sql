drop materialized view if exists monthly_cso;

create materialized view monthly_cso as
select cso.stream_company,
       date_trunc('month', date)::date   as month,
       count(distinct cso.stream_cso_id) as edm_count,
       extract(epoch from sum(start))    as overflowingSeconds,
       count(distinct case
                          when start > interval '30 minutes'
                              then cso.stream_cso_id
           end)                          as overflowing,

       count(distinct case
                          when offline > interval '30 minutes'
                              then cso.stream_cso_id
           end)                          as offline
from stream_summary ss
         join stream_cso cso
              on ss.stream_cso_id = cso.stream_cso_id
group by cso.stream_company,
         date_trunc('month', date)
order by cso.stream_company, month;


drop materialized view if exists daily_cso;

create materialized view daily_cso as
select cso.stream_company,
       date,
       count(*)                       as edm_count,
       extract(epoch from sum(start)) as overflowingSeconds,
       count(distinct case
                          when start > interval '30 minutes'
                              then cso.stream_cso_id
           end)                       as overflowing,

       count(distinct case
                          when offline > interval '30 minutes'
                              then cso.stream_cso_id
           end)                       as offline
from stream_summary ss
         join stream_cso cso on ss.stream_cso_id = cso.stream_cso_id
group by cso.stream_company, date
order by date