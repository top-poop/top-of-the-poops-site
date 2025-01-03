
WITH event_counts AS (
    SELECT
        stream_cso_id,
        COUNT(*) AS event_count
    FROM
        stream_cso_event
    GROUP BY
        stream_cso_id
),
     ranked_masters AS (
         SELECT
             stream_cso_id,
             event_count,
             RANK() OVER (ORDER BY event_count DESC) AS rank
         FROM
             event_counts
     )
SELECT
    stream_cso.stream_company,
    stream_cso.stream_id,
    event_count
FROM
    ranked_masters
join stream_cso on ranked_masters.stream_cso_id = stream_cso.stream_cso_id
WHERE
    rank <= 20
order by event_count desc;



create materialized view stream_unique_events as
WITH ranked_events AS (
    SELECT files.stream_file_id, files.file_time, files.company, content.id, content.status, content.statusstart, content.latesteventstart, content.latesteventend, content.lastupdated,
           ROW_NUMBER() OVER (PARTITION BY status, statusstart, latesteventstart, latesteventend ORDER BY file_time) AS rn
    FROM stream_file_content content
    join stream_files files on content.stream_file_id = files.stream_file_id
)
SELECT *
FROM ranked_events
WHERE rn = 1
order by company, id, file_time
;

