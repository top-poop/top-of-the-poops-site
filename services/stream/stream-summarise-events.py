import argparse
import datetime
import os
import pathlib
from collections import defaultdict
from typing import Mapping, Optional, Iterable

import psycopg2
from psycopg2.extras import DictCursor, execute_batch
from statemachine import statemachine, State

from event_calendar import CSOState, state_name_to_cso_state, StreamMonitorState, Calendar
from psy import select_many
from stream import EventType
from streamdb import Database
from streamdb import StreamEvent


## Takes the events from the database, and turns them into a history view of what was overflowing when
## so we can show a timeseries of all the CSOs

class CalendarListener:

    def __init__(self, start: datetime.date):
        self.monitors: Mapping[str, Calendar] = defaultdict(lambda: Calendar(CSOState.UNKNOWN, start))
        self.current: Optional[Calendar] = None

    def new(self, cso_id: str):
        self.current = self.monitors[cso_id]

    def transition(self, state: CSOState, at: datetime.datetime):
        self.current.add(state, at)

    def things_at(self, dt: datetime.datetime):
        for k, v in self.monitors.items():
            v.add("now", dt)
            yield k, v


class StreamEventStream:

    def __init__(self, cb: CalendarListener):
        self.state = None
        self.monitor = None
        self.state_start = None
        self.cb = cb

    def _next(self, event: StreamEvent):
        self.current_cso = event.cso_id
        self.state = StreamMonitorState(self)
        self.cb.new(event.cso_id)

    def event(self, event: StreamEvent):

        if self.state is None:
            self._next(event)

        if event.cso_id != self.current_cso:
            self._next(event)

        try:
            if event.event == EventType.Start:
                self.state.do_start(stream_event=event)
            elif event.event == EventType.Stop:
                self.state.do_stop(stream_event=event)
        except statemachine.TransitionNotAllowed:
            raise IOError(f"Illegal state transition processing {event}")

    def on_enter_state(self, event, target: State, source, stream_event: StreamEvent):
        self.cb.transition(
            state_name_to_cso_state(target.name),
            at=stream_event.event_time
        )


def row_to_event(row) -> StreamEvent:
    return StreamEvent(
        file_id=row["file_id"],
        event=EventType[row["event"]],
        event_time=row["event_time"],
        update_time=row["update_time"],
        cso_id=row["stream_cso_id"]
    )


def aggregate_stream_events(events: Iterable[StreamEvent]) -> CalendarListener:
    l = CalendarListener(start=start_date)
    s = StreamEventStream(l)

    for event in events:
        s.event(event)
    return l


def events_for_cso(connection, cso_id: str) -> Iterable[StreamEvent]:
    return select_many(
        connection=connection,
        sql="""
            select *
            from stream_cso_event
                     join stream_cso sc on stream_cso_event.stream_cso_id = sc.stream_cso_id
            where stream_id = %(cso_id)s
            order by stream_cso_event.stream_cso_id, event_time""",
        params={"cso_id": cso_id},
        f=row_to_event)


def all_events(connection, since: datetime.date) -> Iterable[StreamEvent]:
    return select_many(
        connection=connection,
        sql="""
            select *
            from stream_cso_event
            where date_trunc('day', event_time) >= %(since)s
            order by stream_cso_id, event_time""",
        params={"since": since},
        f=row_to_event)


def insert_stream_summary(connection, cso_id, allocations):
    with connection.cursor() as cursor:
        execute_batch(
            cur=cursor,
            sql="""insert into stream_summary (stream_cso_id, date, unknown, start, stop, potential_start, offline)
                   values (%(stream_cso_id)s, %(date)s, %(unknown)s, %(start)s, %(stop)s, %(potential_start)s,
                           %(offline)s)
                   on conflict (stream_cso_id, date)
                       do update set unknown         = excluded.unknown,
                                     start           = excluded.start,
                                     stop            = excluded.stop,
                                     potential_start = excluded.potential_start,
                                     offline         = excluded.offline""",
            argslist=[{
                "stream_cso_id": cso_id,
                "date": date,
                "unknown": totals[CSOState.UNKNOWN],
                "start": totals[CSOState.START],
                "stop": totals[CSOState.STOP],
                "potential_start": totals[CSOState.POTENTIAL_START],
                "offline": totals[CSOState.OFFLINE]
            } for date, totals in allocations],
            page_size=1000,
        )
        connection.commit()


if __name__ == "__main__":

    parser = argparse.ArgumentParser(description="Take stream events and turn them into daily summary")
    parser.add_argument("--state", action="store_true", help="Write state transitions to file")
    parser.add_argument("--restart", action="store_true", help="Write information since start of time")
    parser.add_argument("--cso", help="Only a particular cso")
    args = parser.parse_args()

    start_date = datetime.date.fromisoformat("2024-12-01")

    db_host = os.environ.get("DB_HOST", "localhost")

    now = datetime.date.today()
    include_since = now - datetime.timedelta(days=3)

    if args.restart:
        include_since = datetime.date.min

    events_fn = lambda c: all_events(c, since=start_date)

    if args.cso:
        events_fn = lambda c: events_for_cso(c, args.cso)

    with psycopg2.connect(host=db_host, database="gis", user="docker", password="docker",
                          cursor_factory=DictCursor) as conn:

        streamdb = Database(connection=conn)

        print(">> Aggregating...")
        cl = aggregate_stream_events(events=events_fn(conn))

        recent = streamdb.most_recent()

        print(f">> Most recent file: {recent}")

        print(">> Updating Summary...")
        for cso_id, calendar in cl.things_at(recent):
            allocations = calendar.allocations(since=include_since)

            if args.cso:
                print(allocations)
            else:
                insert_stream_summary(conn, cso_id=cso_id, allocations=allocations)

    if args.state:
        try:
            from statemachine.contrib.diagram import DotGraphMachine

            graph = DotGraphMachine(StreamMonitorState)
            dot = graph()
            dot.write_png(pathlib.Path("stream-state-transitions.png"))
        except Exception as e:
            print(f"Can't generate graph: {e}")
