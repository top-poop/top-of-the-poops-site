SELECT
    relname,
    heap_blks_hit,
    heap_blks_read,
    round(100.0 * heap_blks_hit / NULLIF(heap_blks_hit + heap_blks_read, 0), 1) AS cache_hit_ratio
FROM
    pg_statio_user_tables
ORDER BY
    cache_hit_ratio ASC;