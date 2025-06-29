import argparse
import os
import time
from typing import List

import psycopg2
from psycopg2.extras import DictCursor

from args import enum_parser
from companies import WaterCompany
from events import interpret
from streamdb import Database, StreamEvent

if __name__ == '__main__':

    parser = argparse.ArgumentParser(description="Attempt to parse events from stream status files")
    parser.add_argument("--company", type=enum_parser(WaterCompany), nargs="+", help="company (default: all)")
    parser.add_argument("--id", help="id (default: all)")

    args = parser.parse_args()

    db_host = os.environ.get("DB_HOST", "localhost")

    if args.company:
        companies = args.company
    else:
        companies = WaterCompany

    if args.id:
        feature_filter = lambda x: x.id == args.id
    else:
        feature_filter = lambda x: True

    update_materialised_views = False

    with psycopg2.connect(host=db_host, database="gis", user="docker", password="docker",
                          cursor_factory=DictCursor) as conn:

        database = Database(conn)

        for company in companies:

            ids = database.load_ids(company)

            unprocessed_files = database.files_unprocessed(company)
            latest_by_id = database.latest_cso_events(company)

            for file in unprocessed_files:

                print(f"Processing Events from {file}")
                s = time.time()

                new_events: List[StreamEvent] = []

                features = database.load_file_records(file)

                new_cso_features = [f for f in features if f.id not in ids]
                if new_cso_features:
                    print(f"Found {len(new_cso_features)} new CSOs")
                    update_materialised_views = True
                    database.insert_cso(company=company, features=new_cso_features)
                    ids = database.load_ids(company=company)

                for f in [g for g in features if feature_filter(g)]:

                    if f.id == '':
                        print(f">> Record has no id? file = {file}")

                    try:
                        new_event = interpret(ids, file=file, previous=latest_by_id.get(f.id), f=f)
                        if new_event is not None:
                            latest_by_id[f.id] = new_event
                            new_events.append(new_event)

                    except Exception as e:
                        print(f"{f.id} error")
                        raise

                if new_events:
                    database.insert_cso_events(events=new_events)
                    print(f"Inserted {len(new_events)} events in {time.time() - s}")

                database.mark_processed(file)
                conn.commit()

        if update_materialised_views:
            print("Updating views...")
            with conn.cursor() as cursor:
                cursor.execute("refresh materialized view stream_cso_grid")