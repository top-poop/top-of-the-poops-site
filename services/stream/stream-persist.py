import os
import time
from typing import TypeVar, List

import psycopg2

from companies import WaterCompany
from secret import env
from storage import b2_service, Storage
from stream import EventType
from streamdb import Database
from streamdb import StreamEvent

T = TypeVar('T')

if __name__ == '__main__':
    s3 = b2_service(
        env("AWS_ACCESS_KEY_ID", "s3_key_id"),
        env("AWS_SECRET_ACCESS_KEY", "s3_secret_key")
    )
    bucket = s3.Bucket(env("STREAM_BUCKET_NAME", "stream_bucket_name"))
    storage = Storage(bucket)

    db_host = os.environ.get("DB_HOST", "localhost")

    with psycopg2.connect(host=db_host, database="gis", user="docker", password="docker") as conn:

        database = Database(conn)

        for company in WaterCompany:
            last_processed = database.last_processed(company)

            to_process = [ts for ts in storage.available(company=company) if ts > last_processed]

            for ts in to_process:
                print(f"Need to process: {company}: {ts}")
                s = time.time()
                features = storage.load(company, ts)
                print(f"Loaded features in {time.time() - s}")

                ids = database.load_ids(company=company)

                new_csos = [f for f in features if f.id not in ids]
                if new_csos:
                    print(f"\tInserting {len(new_csos)} new csos")
                    database.insert_cso(company, new_csos)
                    ids = database.load_ids(company=company)

                s = time.time()
                most_recent = database.latest_events(company=company)

                new_events: List[StreamEvent] = []

                for f in features:
                    if f.statusStart is None:
                        continue

                    our_event = most_recent.get(f.id)

                    event_type = EventType(int(f.status))

                    match event_type:
                        case EventType.Stop:
                            # if stopped now
                            if our_event is None:
                                if f.latestEventEnd is not None and f.latestEventStart != f.latestEventEnd:
                                    new_events.append(
                                        StreamEvent(id=f.id, event=EventType.Start, event_time=f.latestEventStart,
                                                    update_time=f.lastUpdated))
                                    new_events.append(
                                        StreamEvent(id=f.id, event=EventType.Stop, event_time=f.latestEventEnd,
                                                    update_time=f.lastUpdated))
                                else:
                                    new_events.append(
                                        StreamEvent(id=f.id, event=EventType.Stop, event_time=f.statusStart,
                                                    update_time=f.lastUpdated))
                            else:
                                match our_event.event:
                                    case EventType.Start if our_event.event_time == f.latestEventStart:
                                        # event we started is now stopped
                                        new_events.append(
                                            StreamEvent(id=f.id, event=EventType.Stop, event_time=f.latestEventEnd,
                                                        update_time=f.lastUpdated))
                                    case EventType.Start:
                                        # it stopped, started and stopped between polls, have to bodge
                                        print(f"{f.id} Stopped / Started / Stopped")
                                        new_events.append(
                                            StreamEvent(id=f.id, event=EventType.Stop, event_time=f.latestEventStart,
                                                        update_time=f.lastUpdated))
                                        new_events.append(
                                            StreamEvent(id=f.id, event=EventType.Start, event_time=f.latestEventStart,
                                                        update_time=f.lastUpdated))
                                        new_events.append(
                                            StreamEvent(id=f.id, event=EventType.Stop, event_time=f.latestEventEnd,
                                                        update_time=f.lastUpdated))
                                    case EventType.Stop if our_event.event_time == f.latestEventEnd:
                                        # still stopped and our dates match
                                        pass
                                    case EventType.Stop if f.latestEventStart == f.latestEventEnd:
                                        # still stopped but spurious zero length thing happened
                                        pass
                                    case EventType.Stop:
                                        # something happened in between polling
                                        print(f"{f.id} overflowed in between polls")
                                        new_events.append(
                                            StreamEvent(id=f.id, event=EventType.Start, event_time=f.latestEventStart,
                                                        update_time=f.lastUpdated))
                                        new_events.append(
                                            StreamEvent(id=f.id, event=EventType.Stop, event_time=f.latestEventEnd,
                                                        update_time=f.lastUpdated))
                                    case EventType.Offline:
                                        # went from offline to stop. might have had an event in the middle, ignore for now
                                        new_events.append(
                                            StreamEvent(id=f.id, event=EventType.Stop, event_time=f.statusStart,
                                                        update_time=f.lastUpdated)
                                        )
                                    case _:
                                        print(f"Unhandled {event_type} -> ours was {our_event.event}")

                        case EventType.Start:
                            # is currently overflowing - latestEventStart == statusStart, latestEventEnd == None.
                            if our_event is None:
                                new_events.append(StreamEvent(id=f.id, event=EventType.Start, event_time=f.statusStart,
                                                              update_time=f.lastUpdated))
                            else:
                                match our_event.event:
                                    case EventType.Start if our_event.event_time == f.statusStart:
                                        # we have a start and the events match
                                        print(f"{f.id} still overflowing")
                                    case EventType.Start:
                                        # stopped and started again, lost information...
                                        # we will consider that it was overflowing the whole time
                                        print(f"{f.id} stopped and started")
                                    case EventType.Stop | EventType.Offline:
                                        # our last event was stop or unknown, so we can add a start
                                        print(f"{f.id} started overflowing")
                                        new_events.append(
                                            StreamEvent(id=f.id, event=EventType.Start, event_time=f.statusStart,
                                                        update_time=f.lastUpdated))
                                    case _:
                                        print(f"Unhandled {event_type} -> ours was {our_event.event}")

                        case EventType.Offline:
                            if our_event is None:
                                if f.latestEventEnd is not None and f.latestEventStart != f.latestEventEnd:
                                    new_events.append(
                                        StreamEvent(id=f.id, event=EventType.Start, event_time=f.latestEventStart,
                                                    update_time=f.lastUpdated))
                                    new_events.append(
                                        StreamEvent(id=f.id, event=EventType.Stop, event_time=f.latestEventEnd,
                                                    update_time=f.lastUpdated))
                                new_events.append(
                                    StreamEvent(id=f.id, event=EventType.Offline, event_time=f.statusStart,
                                                update_time=f.lastUpdated))
                            else:
                                match our_event.event:
                                    case EventType.Offline if our_event.event_time == f.statusStart:
                                        # still offline and dates match
                                        pass
                                    case EventType.Offline if f.latestEventStart == f.latestEventEnd:
                                        # still offline but spurious zero length thing happened
                                        pass
                                    case EventType.Offline:
                                        # now offline but overflowed in between polls
                                        new_events.append(
                                            StreamEvent(id=f.id, event=EventType.Start, event_time=f.latestEventStart,
                                                        update_time=f.lastUpdated))
                                        new_events.append(
                                            StreamEvent(id=f.id, event=EventType.Stop, event_time=f.latestEventEnd,
                                                        update_time=f.lastUpdated))
                                        new_events.append(
                                            StreamEvent(id=f.id, event=EventType.Offline, event_time=f.statusStart,
                                                        update_time=f.lastUpdated))
                                    case EventType.Start | EventType.Stop:
                                        new_events.append(
                                            StreamEvent(id=f.id, event=EventType.Offline, event_time=f.statusStart,
                                                        update_time=f.lastUpdated))
                                    case _:
                                        print(f"Unhandled {event_type} -> ours was {our_event.event}")

                database.insert_events(ts, ids=ids, events=new_events)
                print(f"Inserted {len(new_events)} events in {time.time() - s}")

                database.set_last_processed(company=company, dt=ts)
                conn.commit()
