import dataclasses
import datetime
from typing import TypeVar, Callable, Tuple, Iterable, List, Dict, Optional

from psycopg2.extras import execute_batch

from companies import WaterCompany
from stream import FeatureRecord, EventType

T = TypeVar('T')


def iter_row(cursor, size=10, f: Callable[[Tuple], T] = lambda t: t) -> Iterable[T]:
    while True:
        rows = cursor.fetchmany(size)
        if not rows:
            break
        for row in rows:
            yield f(row)


def select_many(connection, sql, params=None, f: Callable[[Tuple], T] = lambda t: t) -> Iterable[T]:
    with connection.cursor() as cursor:
        cursor.execute(sql, params)
        yield from iter_row(cursor, size=100, f=f)


def select_one(connection, sql, params=None, f: Callable[[Tuple], T] = lambda t: t) -> T:
    with connection.cursor() as cursor:
        cursor.execute(sql, params)
        return f(cursor.fetchone())


@dataclasses.dataclass(frozen=True)
class StreamEvent:
    cso_id: str
    event: EventType
    event_time: datetime.datetime
    file_id: str
    update_time: datetime.datetime


@dataclasses.dataclass(frozen=True)
class StreamFile:
    company: WaterCompany
    file_id: str
    file_time: datetime.datetime


class Database:

    def __init__(self, connection):
        self.connection = connection

    def most_recent_loaded(self, company: WaterCompany) -> Optional[StreamFile]:
        things = list(select_many(self.connection,
                                  sql="""
                                      select company, stream_file_id, file_time
                                      from stream_files
                                      where company = %(company)s
                                      order by file_time desc
                                      limit 1
                                      """, params={"company": company.name},
                                  f=lambda row: StreamFile(company=WaterCompany[row["company"]],
                                                           file_id=row["stream_file_id"],
                                                           file_time=row["file_time"])))
        return things[0] if len(things) else None

    def most_recent(self) -> datetime.datetime:
        return select_one(
            connection=self.connection,
            sql="""
                select f.stream_file_id, file_time, process_time
                from stream_files f
                         join stream_files_processed fp on f.stream_file_id = fp.stream_file_id
                order by f.file_time desc
                limit 10
                """,
            f=lambda r: r["file_time"])

    def create_file(self, company: WaterCompany, file_time: datetime.datetime) -> StreamFile:
        with self.connection.cursor() as cursor:
            cursor.execute("""
                           insert into stream_files (company, file_time)
                           values (%(company)s, %(file_time)s)
                           returning stream_file_id""", {
                               "company": company.name,
                               "file_time": file_time
                           })
            result = cursor.fetchone()
            return StreamFile(company=company, file_id=result["stream_file_id"], file_time=file_time)

    def files_unprocessed(self, company: WaterCompany) -> List[StreamFile]:
        return list(
            select_many(self.connection,
                        sql="""
                            select stream_file_id, file_time
                            from stream_files
                            where company = %(company)s
                              and stream_file_id not in
                                  (select stream_file_id
                                   from stream_files_processed
                                   where stream_files_processed.company = %(company)s)
                            order by file_time
                            """,
                        params={
                            "company": company.name
                        },
                        f=lambda result: StreamFile(company=company, file_id=result["stream_file_id"],
                                                    file_time=result['file_time'])))

    def mark_processed(self, file: StreamFile):
        with self.connection.cursor() as cursor:
            cursor.execute(
                """
                insert into stream_files_processed (company, stream_file_id)
                values (%(company)s, %(file_id)s)
                """, {
                    "company": file.company.name,
                    "file_id": file.file_id
                }
            )

    def load_ids(self, company: WaterCompany):
        return {
            r[0]: r[1]
            for r in select_many(
                connection=self.connection,
                sql="""select stream_id, stream_cso_id
                       from stream_cso
                       where stream_company = %(company)s""",
                params={
                    "company": company.name
                })
        }

    def insert_cso_events(self, events: List[StreamEvent]) -> int:
        count = []
        with self.connection.cursor() as cursor:
            for event in events:
                cursor.execute("""
                               insert into stream_cso_event (stream_cso_id, event_time, event, file_id, update_time)
                               values (%(cso_id)s, %(event_time)s, %(event)s, %(file_id)s, %(update_time)s)
                               """, {
                                   "cso_id": event.cso_id,
                                   "event_time": event.event_time,
                                   "event": event.event.name,
                                   "file_id": event.file_id,
                                   "update_time": event.update_time,
                               })
                count.append(cursor.rowcount)
        return sum(count)

    def last_seen_cso(self, company: WaterCompany) -> Dict[str, datetime.datetime]:
        return {c[0]: c[1] for c in select_many(connection=self.connection,
                                                sql="""
                                                    select c.id as stream_id, max(f.file_time) as last_seen_time
                                                    from stream_file_content c
                                                             join stream_files f using (stream_file_id)
                                                    where f.company = %(company)s
                                                    group by c.id
                                                    order by last_seen_time;
                                                    """,
                                                params={
                                                    "company": company.name
                                                },
                                                f=lambda row: (row["stream_id"], row["last_seen_time"]))
                }

    def latest_cso_events(self, company: WaterCompany) -> Dict[str, StreamEvent]:
        return {e[0]: e[1]
                for e in select_many(
                connection=self.connection,
                sql="""
                    WITH ranked_events AS (SELECT e.*,
                                                  ROW_NUMBER()
                                                  OVER (PARTITION BY e.stream_cso_id ORDER BY e.event_time DESC, stream_files.file_time desc) AS rnk
                                           FROM stream_cso_event as e
                                                    join stream_files on stream_files.stream_file_id = e.file_id)
                    SELECT m.stream_id, e.file_id, e.event, e.event_time, e.update_time, m.stream_cso_id
                    FROM stream_cso m
                             JOIN ranked_events e ON m.stream_cso_id = e.stream_cso_id AND e.rnk = 1
                    where m.stream_company = %(company)s;
                    """,
                params={
                    "company": company.name
                },
                f=lambda row: (row["stream_id"], StreamEvent(
                    file_id=row["file_id"],
                    event=EventType[row["event"]],
                    event_time=row["event_time"],
                    update_time=row["update_time"],
                    cso_id=row["stream_cso_id"]
                ))
            )}

    def insert_cso(self, company: WaterCompany, features: List[FeatureRecord]):
        with self.connection.cursor() as cursor:
            for feature in features:
                cursor.execute("""
                               insert into stream_cso (stream_company, stream_id, lat, lon, point)
                               VALUES (%(company)s, %(id)s, %(lat)s, %(lon)s,
                                       st_setsrid(st_makepoint(%(lon)s, %(lat)s), 4326))
                               on conflict (stream_company, stream_id) do nothing
                               """, {
                                   "company": company.name,
                                   "id": feature.id,
                                   "lat": feature.lat,
                                   "lon": feature.lon,
                               })

    def _record_from_row(self, company: WaterCompany, r) -> FeatureRecord:
        return FeatureRecord(
            id=r['id'],
            status=EventType[r['status']],
            company=company.name,
            statusStart=r['statusstart'],
            latestEventStart=r['latesteventstart'],
            latestEventEnd=r['latesteventend'],
            lastUpdated=r['lastupdated'],
            lat=r['lat'],
            lon=r['lon'],
            receivingWater=r['receiving_water'],
        )

    def most_recent_records(self, company: WaterCompany) -> List[FeatureRecord]:
        return list(select_many(connection=self.connection,
                                sql="""
                                    WITH ranked_events AS (SELECT files.stream_file_id,
                                                                  files.file_time,
                                                                  files.company,
                                                                  content.id,
                                                                  content.status,
                                                                  content.statusstart,
                                                                  content.latesteventstart,
                                                                  content.latesteventend,
                                                                  content.lastupdated,
                                                                  content.lat,
                                                                  content.lon,
                                                                  content.receiving_water,
                                                                  ROW_NUMBER() OVER (PARTITION BY id ORDER BY file_time desc) AS rn
                                                           FROM stream_file_events content
                                                                    join stream_files files on content.stream_file_id = files.stream_file_id
                                                           where company = %(company)s)
                                    SELECT *
                                    FROM ranked_events
                                    WHERE rn = 1
                                    order by company, id, file_time, id
                                    """,
                                params={
                                    "company": company.name
                                },
                                f=lambda r: self._record_from_row(company, r))
                    )

    def load_file_records(self, file: StreamFile) -> List[FeatureRecord]:
        return list(
            select_many(
                connection=self.connection,
                sql="""
                    select *
                    from stream_files,
                         stream_file_events
                    where stream_files.stream_file_id = stream_file_events.stream_file_id
                      and stream_files.stream_file_id = %(file_id)s
                    """,
                params={
                    "file_id": file.file_id
                },
                f=lambda row: self._record_from_row(file.company, row))
        )

    def insert_file_events(self, file: StreamFile, features: List[FeatureRecord]):
        self._insert_records("stream_file_events", file, features)

    def insert_file_content(self, file: StreamFile, features: List[FeatureRecord]):
        self._insert_records("stream_file_content", file, features)

    def _insert_records(self, table: str, file: StreamFile, features: List[FeatureRecord]):
        with self.connection.cursor() as cursor:
            execute_batch(
                cur=cursor,
                sql=f"""
                        insert into {table} (stream_file_id, id, status, statusstart, latesteventstart, latesteventend, lastupdated, lat, lon, receiving_water) 
                        values ( %(stream_file_id)s, %(id)s, %(status)s, %(status_start)s, %(latest_event_start)s, %(latest_event_end)s, %(last_updated)s, %(lat)s, %(lon)s, %(receiving_water)s)
                        """,
                argslist=[{
                    "stream_file_id": file.file_id,
                    "id": feature.id,
                    "status": EventType(int(feature.status)).name,
                    "status_start": feature.statusStart,
                    "latest_event_start": feature.latestEventStart,
                    "latest_event_end": feature.latestEventEnd,
                    "last_updated": feature.lastUpdated,
                    "lat": feature.lat,
                    "lon": feature.lon,
                    "receiving_water": feature.receivingWater
                } for feature in features],
                page_size=500,
            )
