from typing import Optional, Dict

from stream import FeatureRecord, EventType
from streamdb import StreamFile, StreamEvent


def bob(mapping: Dict, file: StreamFile, previous: Optional[StreamEvent], f: FeatureRecord) -> Optional[StreamEvent]:
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
            raise NotImplementedError()
