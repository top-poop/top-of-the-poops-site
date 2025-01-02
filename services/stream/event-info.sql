
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


