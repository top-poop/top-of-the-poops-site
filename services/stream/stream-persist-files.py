import argparse
import os

import psycopg2
from psycopg2.extras import DictCursor

from companies import WaterCompany
from secret import env
from args import enum_parser
from storage import b2_service, Storage
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
        companies = WaterCompany

    with psycopg2.connect(host=db_host, database="gis", user="docker", password="docker",
                          cursor_factory=DictCursor) as conn:

        database = Database(conn)

        for company in companies:

            available = [ts for ts in storage.available(company=company)]

            processed = [p.file_time for p in database.processed_files(company=company)]

            to_process = [t for t in available if t not in processed]

            for ts in to_process:
                print(f"Need to process: {company}: {ts}")
                features = storage.load(company, ts)

                file_ref = database.create_file(company=company, file_time=ts)

                database.insert_file(file=file_ref, features=features)
                conn.commit()
