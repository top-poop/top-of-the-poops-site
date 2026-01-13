SELECT
    table_schema || '.' || table_name AS full_table_name,
    pg_size_pretty(pg_total_relation_size('"' || table_schema || '"."' || table_name || '"')) AS total_size,
    pg_size_pretty(pg_relation_size('"' || table_schema || '"."' || table_name || '"')) AS data_size
FROM
    information_schema.tables
WHERE
    table_schema = 'public'
  AND table_type = 'BASE TABLE'
ORDER BY
    pg_total_relation_size('"' || table_schema || '"."' || table_name || '"') DESC;