import collections
import dataclasses
import datetime
from typing import Optional, Dict, DefaultDict

from companies import WaterCompany
from stream import FeatureRecord, EventType
from streamdb import StreamFile, StreamEvent


def _interpret_type_1(mapping: Dict, file: StreamFile, previous: Optional[StreamEvent], f: FeatureRecord) -> Optional[
    StreamEvent]:
    """using status start for the start..."""
    match f.status:
        case EventType.Stop:
            if previous is None:
                return StreamEvent(
                    cso_id=mapping[f.id],
                    event=EventType.Stop,
                    event_time=f.statusStart if f.statusStart is not None else file.file_time,
                    file_id=file.file_id,
                    update_time=f.lastUpdated if f.lastUpdated is not None else file.file_time
                )
            match previous.event:
                case EventType.Start|EventType.Offline:
                    event_time = f.statusStart if f.statusStart is not None else file.file_time
                    if event_time == previous.event_time:
                        event_time += datetime.timedelta(seconds=1)
                    return StreamEvent(
                        cso_id=mapping[f.id],
                        event=f.status,
                        event_time=event_time,
                        file_id=file.file_id,
                        update_time=f.lastUpdated if f.lastUpdated is not None else file.file_time
                    )
                case EventType.Stop:
                    return None
        case EventType.Start:
            if previous is None:
                return StreamEvent(
                    cso_id=mapping[f.id],
                    event=EventType.Start,
                    event_time=f.statusStart,
                    file_id=file.file_id,
                    update_time=f.lastUpdated
                )
            match previous.event:
                case EventType.Stop|EventType.Offline:
                    event_time = f.statusStart
                    if event_time == previous.event_time:
                        event_time += datetime.timedelta(seconds=1)
                    return StreamEvent(
                        cso_id=mapping[f.id],
                        event=f.status,
                        event_time=event_time,
                        file_id=file.file_id,
                        update_time=f.lastUpdated
                    )
                case EventType.Start:
                    return None
        case EventType.Offline:
            if previous is None:
                return StreamEvent(
                    cso_id=mapping[f.id],
                    event=f.status,
                    event_time=f.statusStart,
                    file_id=file.file_id,
                    update_time=f.lastUpdated
                )
            match previous.event:
                case EventType.Stop|EventType.Start:
                    event_time = f.statusStart if f.statusStart is not None else file.file_time
                    if event_time == previous.event_time:
                        event_time += datetime.timedelta(seconds=1)
                    return StreamEvent(
                        cso_id=mapping[f.id],
                        event=f.status,
                        event_time=event_time,
                        file_id=file.file_id,
                        update_time=f.lastUpdated if f.lastUpdated is not None else file.file_time
                    )
                case EventType.Offline:
                    return None


def _bob_ignore(**kwargs):
    return None


def _interpret_type_2(mapping: Dict, file: StreamFile, previous: Optional[StreamEvent], f: FeatureRecord) -> Optional[
    StreamEvent]:
    """using either status start, or latest event end"""
    match f.status:
        case EventType.Stop:
            if previous is None:
                return StreamEvent(
                    cso_id=mapping[f.id],
                    event=EventType.Stop,
                    event_time=f.statusStart if f.statusStart is not None else f.lastUpdated,
                    file_id=file.file_id,
                    update_time=f.lastUpdated
                )
            match previous.event:
                case EventType.Stop:
                    return None
                case _:
                    return StreamEvent(
                        cso_id=mapping[f.id],
                        event=EventType.Stop,
                        event_time=f.latestEventEnd if f.latestEventEnd is not None else f.lastUpdated,
                        file_id=file.file_id,
                        update_time=f.lastUpdated
                    )
        case EventType.Start:
            if previous is None:
                return StreamEvent(
                    cso_id=mapping[f.id],
                    event=EventType.Start,
                    event_time=f.statusStart,
                    file_id=file.file_id,
                    update_time=f.lastUpdated
                )
            match previous.event:
                case EventType.Start:
                    return None
                case _:
                    return StreamEvent(
                        cso_id=mapping[f.id],
                        event=EventType.Start,
                        event_time=f.statusStart,
                        file_id=file.file_id,
                        update_time=f.lastUpdated
                    )
        case EventType.Offline:
            if previous is None:
                return StreamEvent(
                    cso_id=mapping[f.id],
                    event=f.status,
                    event_time=f.statusStart if f.statusStart else f.lastUpdated,
                    file_id=file.file_id,
                    update_time=f.lastUpdated
                )
            match previous.event:
                case EventType.Stop|EventType.Start:
                    return StreamEvent(
                        cso_id=mapping[f.id],
                        event=f.status,
                        event_time=f.latestEventEnd if f.latestEventEnd is not None else f.lastUpdated,
                        file_id=file.file_id,
                        update_time=f.lastUpdated
                    )
                case EventType.Offline:
                    return None

handlers = {
    WaterCompany.Anglian: _interpret_type_1,
    WaterCompany.Northumbrian: _interpret_type_1,
    WaterCompany.SevernTrent: _interpret_type_1,
    WaterCompany.SouthWestWater: _interpret_type_1,
    WaterCompany.Southern: _interpret_type_2,
    WaterCompany.ThamesWater: _interpret_type_2,
    WaterCompany.UnitedUtilities: _interpret_type_2,
    WaterCompany.WessexWater: _interpret_type_2,
    WaterCompany.YorkshireWater: _interpret_type_1,
    WaterCompany.DwrCymru: _interpret_type_1,
}

yorkshire_start_date= datetime.datetime.fromisoformat("2025-01-05 12:00:00.000000 +00:00")

filters = collections.defaultdict(lambda: lambda x: True)
filters[WaterCompany.YorkshireWater]=lambda x: x.statusStart > yorkshire_start_date

def interpret(mapping: Dict, file: StreamFile, previous: Optional[StreamEvent], f: FeatureRecord) -> Optional[
    StreamEvent]:

    if not filters[file.company](f):
        print(f"Filtering out {f}")
        return None

    if f.statusStart is not None and previous is not None and f.statusStart < previous.event_time:
        diff = previous.event_time - f.statusStart
        print(f"{f.id} -> Time Jumped backwards from {previous.event_time} to {f.statusStart} ( by {diff} ) ")
        if f.status != previous.event:
            if f.latestEventEnd is not None and f.latestEventEnd <= previous.event_time:
                f = dataclasses.replace(f, latestEventEnd= previous.event_time + datetime.timedelta(seconds=1))
            f = dataclasses.replace(f, statusStart = previous.event_time + datetime.timedelta(seconds=1))
    output = handlers[file.company](mapping=mapping, file=file, previous=previous, f=f)
    if output is not None:
        assert(output.event_time is not None)
    return output
