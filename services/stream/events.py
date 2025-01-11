from typing import Optional, Dict

from companies import WaterCompany
from stream import FeatureRecord, EventType
from streamdb import StreamFile, StreamEvent


def _bob_type1(mapping: Dict, file: StreamFile, previous: Optional[StreamEvent], f: FeatureRecord) -> Optional[
    StreamEvent]:
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


def _bob_southern(mapping: Dict, file: StreamFile, previous: Optional[StreamEvent], f: FeatureRecord) -> Optional[
    StreamEvent]:
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
            return StreamEvent(
                cso_id=mapping[f.id],
                event=EventType.Stop,
                event_time=f.latestEventEnd,
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
    WaterCompany.Anglian: _bob_type1,
    WaterCompany.Northumbrian: _bob_type1,
    WaterCompany.SevernTrent: _bob_type1,
    WaterCompany.Southern: _bob_southern
}


def bob(mapping: Dict, file: StreamFile, previous: Optional[StreamEvent], f: FeatureRecord) -> Optional[StreamEvent]:
    if f.statusStart is not None and previous is not None and f.statusStart < previous.event_time:
        raise NotImplementedError("events moving backwards")
    return handlers.get(file.company, _bob_ignore)(mapping=mapping, file=file, previous=previous, f=f)
