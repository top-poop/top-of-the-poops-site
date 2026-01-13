SELECT
    c.relname AS table,
    count(*) * 8 / 1024.0 AS mb_cached,
    round(100.0 * count(*) / total.total, 1) AS percent_cached
FROM
    pg_buffercache b
        JOIN
    pg_class c ON b.relfilenode = pg_relation_filenode(c.oid)
        JOIN
    pg_database d ON b.reldatabase = d.oid AND d.datname = current_database(),
    (SELECT count(*) AS total FROM pg_buffercache) AS total
GROUP BY
    c.relname, total.total
order by percent_cached desc
