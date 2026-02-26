import collections
import dataclasses
import datetime
import logging
from typing import Optional, Dict, DefaultDict

from companies import WaterCompany
from stream import FeatureRecord, EventType
from streamdb import StreamFile, StreamEvent

logger = logging.getLogger(__name__)

def not_none(*things):
    for t in things:
        if t is not None:
            return t
    return None


def sequentially_safe_date(
        last: datetime.datetime,
        this_event_start: datetime.datetime,
        this_event_updated: datetime.datetime,
        this_file_time: datetime.datetime):
    event_time = not_none(this_event_start, this_event_updated, this_file_time)

    if event_time <= last:
        event_time = last + datetime.timedelta(seconds=1)

    return event_time


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
                case EventType.Start | EventType.Offline:
                    event_time = sequentially_safe_date(last=previous.event_time, this_event_start=f.statusStart,
                                                        this_event_updated=f.lastUpdated, this_file_time=file.file_time)
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
                case EventType.Stop | EventType.Offline:
                    event_time = sequentially_safe_date(last=previous.event_time, this_event_start=f.statusStart,
                                                        this_event_updated=f.lastUpdated, this_file_time=file.file_time)
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
                case EventType.Stop | EventType.Start:
                    event_time = sequentially_safe_date(last=previous.event_time, this_event_start=f.statusStart,
                                                        this_event_updated=f.lastUpdated, this_file_time=file.file_time)
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

                    # for a stop, have to pick later of statusStart, or latestEventEnd, as any  can be used.

                    date_to_use = max((dt for dt in [f.statusStart, f.latestEventEnd] if dt is not None), default=None)
                    # some records just dont have dates in so keep trying...
                    if date_to_use is None:
                        date_to_use = f.latestEventStart
                    if date_to_use is None:
                        date_to_use = f.lastUpdated

                    event_time = sequentially_safe_date(last=previous.event_time, this_event_start=date_to_use,
                                                        this_event_updated=f.lastUpdated, this_file_time=file.file_time)
                    return StreamEvent(
                        cso_id=mapping[f.id],
                        event=EventType.Stop,
                        event_time=event_time,
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
                    event_time = sequentially_safe_date(last=previous.event_time, this_event_start=f.statusStart,
                                                        this_event_updated=f.lastUpdated, this_file_time=file.file_time)
                    return StreamEvent(
                        cso_id=mapping[f.id],
                        event=EventType.Start,
                        event_time=event_time,
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
                case EventType.Stop | EventType.Start:
                    event_time = sequentially_safe_date(last=previous.event_time, this_event_start=f.statusStart,
                                                        this_event_updated=f.lastUpdated, this_file_time=file.file_time)
                    return StreamEvent(
                        cso_id=mapping[f.id],
                        event=f.status,
                        event_time=event_time,
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

yorkshire_start_date = datetime.datetime.fromisoformat("2025-01-05 12:00:00.000000 +00:00")

filters = collections.defaultdict(lambda: lambda x: True)
filters[WaterCompany.YorkshireWater] = lambda x: x.statusStart > yorkshire_start_date


def interpret(mapping: Dict, file: StreamFile, previous: Optional[StreamEvent], f: FeatureRecord) -> Optional[
    StreamEvent]:
    if not filters[file.company](f):
        print(f"Filtering out {f}")
        return None

    try:
        output = handlers[file.company](mapping=mapping, file=file, previous=previous, f=f)

        if output is not None:
            assert (output.event_time is not None)
            if previous is not None:
                assert(output.event_time > previous.event_time)
        return output
    except Exception as e:
        logger.error(f"{e}: Error processing {file}: Record: {f}    Previous: {previous}")
        raise
