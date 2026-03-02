SELECT
    pid,
    usename AS user,
    datname AS database,
    state,
    query_start,
    now() - query_start AS duration,
    query
FROM
    pg_stat_activity
WHERE
    state <> 'idle'
ORDER BY
    query_start DESC;