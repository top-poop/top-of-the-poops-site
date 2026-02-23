import datetime
from collections import defaultdict
from enum import Enum
from typing import Dict, Tuple, List

from statemachine import StateMachine, State

_ZERO = datetime.timedelta(seconds=0)


class CSOState(Enum):
    UNKNOWN = 0
    START = 1
    STOP = 2
    OFFLINE = 3
    POTENTIAL_START = 4



class DayBucket:
    def __init__(self):
        self.bucket: Dict[CSOState, datetime.timedelta] = defaultdict(lambda: _ZERO)
        self.total = datetime.timedelta(seconds=0)

    def allocate(self, state: CSOState, delta: datetime.timedelta):
        self.total += delta
        self.bucket[state] += delta

        if self.total > datetime.timedelta(days=1):
            raise ValueError("Can only have one day's worth of time in a bucket")

        return self

    def totals(self):
        return self.bucket


def at_midnight(d: datetime.date):
    return datetime.datetime(d.year, d.month, d.day).replace(tzinfo=datetime.UTC)


class Calendar:
    def __init__(self, initial: CSOState, start: datetime.date):
        self.buckets: Dict[datetime.date, DayBucket] = defaultdict(lambda: DayBucket())

        self.current = initial
        self.last = at_midnight(start)

    def add(self, state: CSOState, at: datetime.datetime):

        if at < self.last:
            raise ValueError(f"Events must be in sequence, last {self.last}, this {at}")

        day = self.last.date()

        while day <= at.date():

            bucket = self.buckets[day]

            day_midnight = at_midnight(day)
            next_day = day_midnight + datetime.timedelta(days=1)

            if self.last > day_midnight and at < next_day:
                delta = at - self.last
            elif self.last > day_midnight:
                delta = next_day - self.last
            elif at < next_day:
                delta = at - day_midnight
            else:
                delta = next_day - day_midnight

            if delta.total_seconds() > 0.0:
                bucket.allocate(self.current, delta)

            day += datetime.timedelta(days=1)

        self.current = state
        self.last = at

    def allocations(self, since:datetime.date) -> List[Tuple]:
        dates = sorted(self.buckets.keys())
        return [(d, self.buckets[d].totals()) for d in dates if d >= since and self.buckets[d].total.total_seconds() > 0]


def date_of(s: str) -> datetime.date:
    return datetime.date.fromisoformat(s)


def datetime_of(s: str) -> datetime.datetime:
    return datetime.datetime.fromisoformat(s).replace(tzinfo=datetime.UTC)

def state_name_to_cso_state(s:str) -> CSOState:
    match s:
        case 'Unknown':
            return CSOState.UNKNOWN
        case 'Start':
            return CSOState.START
        case 'Stop':
            return CSOState.STOP
        case 'Offline':
            return CSOState.OFFLINE
        case 'Potentially Overflowing':
            return CSOState.POTENTIAL_START
        case _:
            raise ValueError(f"State name of {s} is not known")


class StreamMonitorState(StateMachine):
    unknown = State("Unknown", initial=True)
    start = State("Start")
    stop = State("Stop")
    offline = State("Offline")
    potentially_overflowing = State("Potentially Overflowing")

    do_start = unknown.to(start) | stop.to(start) | start.to.itself() | potentially_overflowing.to(start) | offline.to(start)
    do_stop = unknown.to(stop) | start.to(stop) | offline.to(stop) | stop.to.itself() | potentially_overflowing.to(stop)
    do_offline = unknown.to(offline) | stop.to(offline) | offline.to.itself() | start.to(potentially_overflowing) | potentially_overflowing.to(potentially_overflowing)

    def __init__(self, cb):
        super(StreamMonitorState, self).__init__()
        self.add_listener(cb)


def test_calendar_with_single_event_spanning_multiple_days():
    c = Calendar(CSOState.STOP, date_of("2023-01-01"))

    c.add(CSOState.START, datetime_of("2023-01-02 02:00"))
    c.add(CSOState.STOP, at_midnight(datetime_of("2023-01-04")))
    b = c.allocations(since=datetime.date.min)

    assert len(b) == 3
    assert b[0] == (date_of("2023-01-01"), {CSOState.STOP: datetime.timedelta(days=1)})
    assert b[1] == (
        date_of("2023-01-02"),
        {CSOState.STOP: datetime.timedelta(hours=2), CSOState.START: datetime.timedelta(hours=22)})
    assert b[2] == (date_of("2023-01-03"), {CSOState.START: datetime.timedelta(hours=24)})
