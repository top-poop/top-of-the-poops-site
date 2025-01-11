from typing import Optional, Dict

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
                    update_time=f.lastUpdated if f.statusStart is not None else file.file_time
                )
            match previous.event:
                case EventType.Start:
                    return StreamEvent(
                        cso_id=mapping[f.id],
                        event=EventType.Stop,
                        event_time=f.statusStart if f.statusStart is not None else file.file_time,
                        file_id=file.file_id,
                        update_time=f.lastUpdated if f.statusStart is not None else file.file_time
                    )
                case EventType.Stop:
                    return None
            raise NotImplementedError()
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
                case EventType.Stop:
                    return StreamEvent(
                        cso_id=mapping[f.id],
                        event=EventType.Start,
                        event_time=f.statusStart,
                        file_id=file.file_id,
                        update_time=f.lastUpdated
                    )
                case EventType.Start:
                    return None
            raise NotImplementedError()
        case EventType.Offline:
            # ignore offline for now
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
            return StreamEvent(
                cso_id=mapping[f.id],
                event=EventType.Start,
                event_time=f.statusStart,
                file_id=file.file_id,
                update_time=f.lastUpdated
            )


handlers = {
    WaterCompany.Anglian: _interpret_type_1,
    WaterCompany.Northumbrian: _interpret_type_1,
    WaterCompany.SevernTrent: _interpret_type_1,
    WaterCompany.SouthWestWater: _interpret_type_1,
    WaterCompany.Southern: _interpret_type_2,
    WaterCompany.ThamesWater: _interpret_type_2,
    WaterCompany.UnitedUtilities: _interpret_type_2,
    WaterCompany.WessexWater: _interpret_type_2,
}


def interpret(mapping: Dict, file: StreamFile, previous: Optional[StreamEvent], f: FeatureRecord) -> Optional[
    StreamEvent]:
    if f.statusStart is not None and previous is not None and f.statusStart < previous.event_time:
        diff = previous.event_time - f.statusStart
        print(f"{f.id} -> Time Jumped backwards from {previous.event_time} to {f.statusStart} ( by {diff} ) ")
        return None
    output = handlers[file.company](mapping=mapping, file=file, previous=previous, f=f)
    if output is not None:
        assert(output.event_time is not None)
    return output
