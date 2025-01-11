import argparse
import os
import time
from typing import List

import psycopg2
from psycopg2.extras import DictCursor

from args import enum_parser
from companies import WaterCompany
from events import interpret
from secret import env
from storage import b2_service, Storage
from streamdb import Database, StreamEvent

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

    with psycopg2.connect(host=db_host, database="gis", user="docker", password="docker",
                          cursor_factory=DictCursor) as conn:

        database = Database(conn)

        for company in companies:

            ids = database.load_ids(company)

            unprocessed_files = database.files_unprocessed(company)

            for file in unprocessed_files:

                new_events: List[StreamEvent] = []

                latest_by_id = database.latest_events(company)

                features = database.load_file_events(file)

                for f in [g for g in features if feature_filter(g)]:
                    try:
                        if f.statusStart is None:
                            continue

                        our_event = latest_by_id.get(f.id)

                        computed = interpret(ids, file=file, previous=latest_by_id.get(f.id), f=f)

                    except Exception as e:
                        print(f"{f.id} error")
                        raise

                if new_events:
                    database.insert_events(events=new_events)
                    print(f"Inserted {len(new_events)} events in {time.time() - s}")

                database.mark_processed(file)
                conn.commit()
