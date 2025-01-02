import dataclasses
import datetime
from typing import TypeVar, Callable, Tuple, Iterable, List, Dict

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
    id: str
    event: EventType
    event_time: datetime.datetime
    update_time: datetime.datetime


class Database:

    def __init__(self, connection):
        self.connection = connection

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

    def latest_events(self, company: WaterCompany) -> Dict[str, StreamEvent]:
        return {row[0]: StreamEvent(event=EventType[row[1]], id=row[0], event_time=row[2], update_time=row[3])
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
SELECT m.stream_id, e.event, e.event_time, e.update_time
FROM stream_cso m
JOIN ranked_events e ON m.stream_cso_id = e.stream_cso_id AND e.rnk = 1
where m.stream_company = %(company)s;
                           """,
                params={
                    "company": company.name
                }
            )}

    def insert_events(self, file_date: datetime.datetime, ids: Dict[str, str], events: List[StreamEvent]) -> int:
        count = []
        with self.connection.cursor() as cursor:
            for event in events:
                cursor.execute("""
                insert into stream_cso_event (stream_cso_id, event_time, event, file_time, update_time) 
                values(%(cso_id)s, %(event_time)s, %(event)s, %(file_time)s, %(update_time)s)
                on conflict ( stream_cso_id, event_time, event) do nothing 
                """, {
                    "cso_id": ids[event.id],
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

    def insert_file(self, company: WaterCompany, dt: datetime.datetime, features: List[FeatureRecord]):
        with self.connection.cursor() as cursor:
            for feature in features:
                cursor.execute("""
                insert into stream_files (company, file_time, id, status, statusstart, latesteventstart, latesteventend, lastupdated) 
                values ( %(company)s, %(file_time)s, %(id)s, %(status)s, %(status_start)s, %(latest_event_start)s, %(latest_event_end)s, %(last_updated)s)
                """, {
                    "company": company.name,
                    "file_time": dt,
                    "id": feature.id,
                    "status": EventType(int(feature.status)).name,
                    "status_start": feature.statusStart,
                    "latest_event_start": feature.latestEventStart,
                    "latest_event_end": feature.latestEventEnd,
                    "last_updated": feature.lastUpdated,
                })
