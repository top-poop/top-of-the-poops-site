drop table if exists stream_cso cascade;

create table stream_cso
(
    stream_cso_id  uuid primary key default gen_random_uuid(),
    stream_company text,
    stream_id      text,
    lat            float,
    lon            float,
    point          geometry(point, 4326),
    first_seen     timestamptz      default now()
);

create unique index stream_cso_idx1 on stream_cso (stream_company, stream_id);

drop table if exists stream_cso_event;

create table stream_cso_event
(
    stream_cso_id uuid references stream_cso (stream_cso_id),
    event_time    timestamptz,
    event         text,
    file_time     timestamptz,
    update_time   timestamptz
);


drop table if exists stream_process;

create table stream_process
(
    company        text primary key,
    last_processed timestamptz
);


drop table if exists stream_files;

create table stream_files (
    company text,
    file_time timestamptz,
    id text,
    status text,
    statusStart timestamptz,
    latestEventStart timestamptz,
    latestEventEnd timestamptz,
    lastUpdated timestamptz
);

create unique index stream_files_idx1 on stream_files(company, file_time, id);



drop table if exists stream_cso_consent;

-- create table stream_cso_consent (
--
-- )