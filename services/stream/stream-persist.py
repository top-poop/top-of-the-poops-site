import argparse
import os
import time
from typing import List

import psycopg2

from args import enum_parser
from companies import WaterCompany
from secret import env
from storage import b2_service, Storage
from stream import EventType
from streamdb import Database
from streamdb import StreamEvent

if __name__ == '__main__':

    parser = argparse.ArgumentParser(description="Attempt to parse events from stream status files")
    parser.add_argument("--company", type=enum_parser(WaterCompany), nargs="+", help="company (default: all)")
    parser.add_argument("--id", help="id (default: all)")

    args = parser.parse_args()

    s3 = b2_service(
        env("AWS_ACCESS_KEY_ID", "s3_key_id"),
        env("AWS_SECRET_ACCESS_KEY", "s3_secret_key")
    )
    bucket = s3.Bucket(env("STREAM_BUCKET_NAME", "stream_bucket_name"))
    storage = Storage(bucket)

    db_host = os.environ.get("DB_HOST", "localhost")

    if args.company:
        companies = args.company
    else:
        companies = WaterCompany

    if args.id:
        feature_filter = lambda x: x.id == args.id
    else:
        feature_filter = lambda x: True

    with psycopg2.connect(host=db_host, database="gis", user="docker", password="docker") as conn:

        database = Database(conn)

        for company in companies:
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

                for f in [g for g in features if feature_filter(g)]:
                    try:
                        if f.statusStart is None:
                            continue

                        event_type = EventType(int(f.status))

                        our_event = most_recent.get(f.id)

                        if our_event is not None and our_event.event_time > f.statusStart:
                            print(
                                f"{f.id} - out of sequence -> last event {our_event.event}:{our_event.event_time} is after current {event_type}:{f.statusStart}")
                            ## out of sequence! - remove most event and try again.
                            database.remove_event(our_event)
                            most_recent = database.latest_events(company=company)
                            our_event = most_recent.get(f.id)

                        match event_type:
                            case EventType.Stop:
                                # if stopped now
                                if our_event is None:
                                    if f.latestEventEnd is not None and f.latestEventStart < f.latestEventEnd <= f.statusStart:
                                        new_events.append(
                                            StreamEvent(cso_id=ids[f.id], event=EventType.Start,
                                                        event_time=f.latestEventStart,
                                                        update_time=f.lastUpdated))
                                        new_events.append(
                                            StreamEvent(cso_id=ids[f.id], event=EventType.Stop,
                                                        event_time=f.latestEventEnd,
                                                        update_time=f.lastUpdated))
                                    else:
                                        new_events.append(
                                            StreamEvent(cso_id=ids[f.id], event=EventType.Stop,
                                                        event_time=f.statusStart,
                                                        update_time=f.lastUpdated))
                                else:
                                    match our_event.event:
                                        case EventType.Start if f.latestEventEnd:
                                            new_events.append(
                                                StreamEvent(cso_id=ids[f.id], event=EventType.Stop,
                                                            event_time=f.latestEventEnd,
                                                            update_time=f.lastUpdated))
                                        case EventType.Start:
                                            pass
                                        case EventType.Stop:
                                            pass
                                        case _:
                                            print(f"Unhandled {event_type} -> ours was {our_event.event}")

                            case EventType.Start:
                                # is currently overflowing - latestEventStart == statusStart, latestEventEnd == None.
                                if our_event is None:
                                    new_events.append(
                                        StreamEvent(cso_id=ids[f.id], event=EventType.Start, event_time=f.statusStart,
                                                    update_time=f.lastUpdated))
                                else:
                                    match our_event.event:
                                        case EventType.Start:
                                            pass
                                        case EventType.Stop | EventType.Offline:
                                            # our last event was stop or unknown, so we can add a start
                                            print(f"{f.id} started overflowing")
                                            new_events.append(
                                                StreamEvent(cso_id=ids[f.id], event=EventType.Start,
                                                            event_time=f.statusStart,
                                                            update_time=f.lastUpdated))
                                        case _:
                                            print(f"Unhandled {event_type} -> ours was {our_event.event}")

                            case EventType.Offline:
                                pass
                    except Exception as e:
                        print(f"{f.id} error")
                        raise

                if new_events:
                    database.insert_events(ts, events=new_events)
                    print(f"Inserted {len(new_events)} events in {time.time() - s}")

                database.set_last_processed(company=company, dt=ts)
                conn.commit()
