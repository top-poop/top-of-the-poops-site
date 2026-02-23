import argparse
import datetime
import logging
import os
import pathlib
from collections import defaultdict
from typing import Mapping, Optional, Iterable

from statemachine import statemachine, State

import psy
from event_calendar import CSOState, state_name_to_cso_state, StreamMonitorState, Calendar
from psy import select_many
from stream import EventType
from streamdb import Database
from streamdb import StreamEvent

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s.%(msecs)03dZ %(levelname)s [%(name)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)

logger = logging.getLogger(__name__)

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
            elif event.event == EventType.Offline:
                self.state.do_offline(stream_event=event)
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
    count = 0

    l = CalendarListener(start=start_date)
    s = StreamEventStream(l)

    for event in events:
        s.event(event)
        count += 1
        if count % 10_000 == 0:
            print(f"Processed {count} events so far")
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


def insert_stream_summary(connection, everything):
    logger.info(f"Have {len(everything)} items to insert")
    with connection.cursor() as cursor:
        cursor.execute("""
                       CREATE
                       TEMP TABLE tmp_stream_summary (LIKE stream_summary INCLUDING DEFAULTS INCLUDING CONSTRAINTS) on commit drop;
                       """)

        count = 0

        with cursor.copy(f"COPY tmp_stream_summary FROM STDIN") as copy:
            for cso_id, allocations in everything:
                for date, totals in allocations:
                    count += 1
                    copy.write_row((
                        cso_id,
                        date,
                        totals[CSOState.UNKNOWN],
                        totals[CSOState.START],
                        totals[CSOState.STOP],
                        totals[CSOState.POTENTIAL_START],
                        totals[CSOState.OFFLINE]
                    ))

        logger.info(f"All {count} rows inserted to temp table")

        cursor.execute("""
                       INSERT INTO stream_summary
                       SELECT *
                       FROM tmp_stream_summary ON CONFLICT (stream_cso_id, date)
            DO
                       UPDATE SET
                           unknown = EXCLUDED.unknown,
                       start = EXCLUDED.start
                           , stop = EXCLUDED.stop
                           , potential_start = EXCLUDED.potential_start
                           , offline = EXCLUDED.offline
                       """)
        logger.info("Copied across")
        connection.commit()


def filter_events(max_date: datetime.datetime, events: Iterable[StreamEvent]) -> Iterable[StreamEvent]:
    # wessex water reported some events far in the future.
    for event in events:
        if event.event_time <= max_date:
            yield event
        else:
            print(f"Skipping because its in the future: {event}")


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

    print(f"Including events since: {include_since}")

    events_fn = lambda c: all_events(c, since=start_date)

    if args.cso:
        events_fn = lambda c: events_for_cso(c, args.cso)

    pool = psy.connect(db_host)

    with pool.connection() as conn:

        streamdb = Database(connection=conn)

        recent = streamdb.most_recent()
        print(f">> Most recent file: {recent}")

        print(">> Aggregating...")
        filtered_events = filter_events(recent, events_fn(conn))

        cl = aggregate_stream_events(events=filtered_events)

        print(">> Updating Summary...")

        everything = [
            (cso_id, calendar.allocations(since=include_since))
            for cso_id, calendar in cl.things_at(recent)
        ]

        insert_stream_summary(conn, everything)

    if args.state:
        try:
            from statemachine.contrib.diagram import DotGraphMachine

            graph = DotGraphMachine(StreamMonitorState)
            dot = graph()
            dot.write_png(pathlib.Path("stream-state-transitions.png"))
        except Exception as e:
            print(f"Can't generate graph: {e}")
