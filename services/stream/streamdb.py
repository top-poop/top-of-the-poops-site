import dataclasses
import datetime
from typing import TypeVar, Callable, Tuple, Iterable, List, Dict

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


def select_one(connection, sql, params=None):
    with connection.cursor() as cursor:
        cursor.execute(sql, params)
        return cursor.fetchone()


@dataclasses.dataclass(frozen=True)
class StreamEvent:
    cso_id: str
    event: EventType
    event_time: datetime.datetime
    update_time: datetime.datetime


@dataclasses.dataclass(frozen=True)
class StreamFile:
    company: WaterCompany
    file_id: str
    file_time: datetime.datetime


class Database:

    def __init__(self, connection):
        self.connection = connection

    def processed_files(self, company: WaterCompany) -> List[StreamFile]:
        return list(select_many(self.connection,
                                sql="""
        select company, stream_file_id, file_time 
            from stream_files 
            where company = %(company)s""",
                                params={
                                    "company": company.name
                                },
                                f=lambda row: StreamFile(
                                    company=WaterCompany[row["company"]],
                                    file_id=row["stream_file_id"],
                                    file_time=row["file_time"])
                                ))

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

    def last_processed(self, company: WaterCompany) -> datetime.datetime:
        row = list(select_many(self.connection,
                               sql="""select last_processed from stream_process where company = %(company)s""",
                               params={
                                   "company": company.name
                               }))
        if row:
            return row[0][0]
        else:
            return datetime.datetime.fromtimestamp(0, tz=datetime.UTC)

    def set_last_processed(self, company: WaterCompany, dt: datetime.datetime):
        with self.connection.cursor() as cursor:
            cursor.execute(
                """
            insert into stream_process (company, last_processed) values (%(company)s, %(date)s)
            on conflict (company) do update set last_processed = excluded.last_processed
            """, {
                    "company": company.name,
                    "date": dt
                }
            )

    def load_ids(self, company: WaterCompany):
        return {
            r[0]: r[1]
            for r in select_many(
                connection=self.connection,
                sql="""select stream_id, stream_cso_id from stream_cso where stream_company = %(company)s""",
                params={
                    "company": company.name
                })
        }

    def remove_event(self, event: StreamEvent):
        with self.connection.cursor() as cursor:
            cursor.execute(
                """delete from stream_cso_event 
                where stream_cso_id = %(id)s and event = %(event)s and event_time = %(event_time)s""",
                {
                    "id": event.cso_id,
                    "event": event.event.name,
                    "event_time": event.event_time
                }
            )

    def latest_events(self, company: WaterCompany) -> Dict[str, StreamEvent]:
        return {row[0]: StreamEvent(event=EventType[row[1]], event_time=row[2], update_time=row[3],
                                    cso_id=row[4])
                for row in select_many(
                connection=self.connection,
                sql="""
WITH ranked_events AS (
    SELECT
        e.*,
        ROW_NUMBER() OVER (PARTITION BY e.stream_cso_id ORDER BY e.event_time DESC, e.file_time desc) AS rnk
    FROM
        stream_cso_event as e
)
SELECT m.stream_id, e.event, e.event_time, e.update_time, m.stream_cso_id
FROM stream_cso m
JOIN ranked_events e ON m.stream_cso_id = e.stream_cso_id AND e.rnk = 1
where m.stream_company = %(company)s;
                           """,
                params={
                    "company": company.name
                }
            )}

    def insert_events(self, file_date: datetime.datetime, events: List[StreamEvent]) -> int:
        count = []
        with self.connection.cursor() as cursor:
            for event in events:
                cursor.execute("""
                insert into stream_cso_event (stream_cso_id, event_time, event, file_time, update_time) 
                values(%(cso_id)s, %(event_time)s, %(event)s, %(file_time)s, %(update_time)s)
                """, {
                    "cso_id": event.cso_id,
                    "event_time": event.event_time,
                    "event": event.event.name,
                    "file_time": file_date,
                    "update_time": event.update_time,
                })
                count.append(cursor.rowcount)
        return sum(count)

    def insert_cso(self, company: WaterCompany, features: List[FeatureRecord]):
        with self.connection.cursor() as cursor:
            for feature in features:
                cursor.execute("""
                insert into stream_cso (stream_company, stream_id, lat, lon, point) 
                VALUES (%(company)s,%(id)s,%(lat)s,%(lon)s,st_setsrid(st_makepoint(%(lon)s, %(lat)s), 4326))
                on conflict (stream_company, stream_id) do nothing
                """, {
                    "company": company.name,
                    "id": feature.id,
                    "lat": feature.lat,
                    "lon": feature.lon,
                })

    def most_recent_records(self, company: WaterCompany) -> List[FeatureRecord]:
        return list(select_many(connection=self.connection,
                                sql="""

WITH ranked_events AS (
    SELECT files.stream_file_id, files.file_time, files.company, content.id, content.status, content.statusstart, content.latesteventstart, content.latesteventend, content.lastupdated, content.lat, content.lon, content.receiving_water,
           ROW_NUMBER() OVER (PARTITION BY id, status, statusstart, latesteventstart, latesteventend ORDER BY file_time desc) AS rn
    FROM stream_file_content content
             join stream_files files on content.stream_file_id = files.stream_file_id
    where company=%(company)s
)
SELECT *
FROM ranked_events
WHERE rn = 1
order by company, id, file_time, id
                        """,
                                params={
                                    "company": company.name
                                },
                                f=lambda r: FeatureRecord(
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
                                ))
                    )

    def insert_file(self, file: StreamFile, features: List[FeatureRecord]):
        with self.connection.cursor() as cursor:
            execute_batch(
                cur=cursor,
                sql="""
                insert into stream_file_content (stream_file_id, id, status, statusstart, latesteventstart, latesteventend, lastupdated, lat, lon, receiving_water) 
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
