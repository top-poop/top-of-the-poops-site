import argparse
import os
from dataclasses import replace

import psycopg2
from psycopg2.extras import DictCursor

from args import enum_parser
from companies import WaterCompany
from secret import env
from storage import b2_service, Storage
from stream import FeatureRecord, EventType
from streamdb import Database

if __name__ == '__main__':

    parser = argparse.ArgumentParser(description="Attempt to parse events from stream status files")
    parser.add_argument("--company", type=enum_parser(WaterCompany), nargs="+", help="company (default: all)")

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
        companies = [w for w in WaterCompany if w != WaterCompany.YorkshireWater]

    with psycopg2.connect(host=db_host, database="gis", user="docker", password="docker",
                          cursor_factory=DictCursor) as conn:

        database = Database(conn)

        for company in companies:

            available = [ts for ts in storage.available(company=company)]

            processed = [p.file_time for p in database.processed_files(company=company)]

            to_process = [t for t in available if t not in processed]

            most_recent = database.most_recent_records(company=company)
            most_recent_by_id = {x.id: x for x in most_recent}

            for ts in to_process:
                print(f"Need to process: {company}: {ts}")

                file_ref = database.create_file(company=company, file_time=ts)

                features = storage.load(company, ts)

                def want(f: FeatureRecord):
                    if f.id not in most_recent_by_id:
                        return True
                    r = most_recent_by_id[f.id]
                    new_feature = (EventType(int(f.status)), f.statusStart, f.latestEventStart, f.latestEventEnd)
                    existing_feature = (r.status, r.statusStart, r.latestEventStart, r.latestEventEnd)
                    return new_feature != existing_feature


                wanted_features = [f for f in features if want(f)]
                print(f"Got {len(wanted_features)} events")
                database.insert_file(file=file_ref, features=wanted_features)

                most_recent_by_id.update({w.id: replace(w, status=EventType(int(w.status))) for w in wanted_features})
                conn.commit()
