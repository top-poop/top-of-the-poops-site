drop table if exists stream_files cascade;

create table stream_files
(
    stream_file_id uuid primary key default gen_random_uuid(),
    company        text,
    file_time      timestamptz
);

create unique index stream_files_idx1 on stream_files (company, file_time);

drop table if exists stream_file_events cascade;

create table stream_file_events
(
    stream_file_id   uuid,
    id               text,
    status           text,
    statusStart      timestamptz,
    latestEventStart timestamptz,
    latestEventEnd   timestamptz,
    lastUpdated      timestamptz,
    lat              float,
    lon              float,
    receiving_water  text
);

create unique index stream_file_events_idx1 on stream_file_events (stream_file_id, id);

drop table if exists stream_file_content cascade;

create table stream_file_content
(
    stream_file_id   uuid,
    id               text,
    status           text,
    statusStart      timestamptz,
    latestEventStart timestamptz,
    latestEventEnd   timestamptz,
    lastUpdated      timestamptz,
    lat              float,
    lon              float,
    receiving_water  text
);

create unique index stream_file_content_idx1 on stream_file_content (stream_file_id, id);


drop table if exists stream_process;

create table stream_process
(
    company        text primary key,
    last_processed timestamptz
);

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
    stream_cso_id uuid,
    event_time    timestamptz,
    event         text,
    file_time     timestamptz,
    update_time   timestamptz
);



drop table if exists stream_cso_consent;

-- create table stream_cso_consent (
--
-- )