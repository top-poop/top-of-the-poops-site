import os
from typing import TypeVar

import psycopg2

from companies import WaterCompany
from secret import env
from storage import b2_service, Storage
from streamdb import Database

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

            to_process = [ts for ts in storage.available(company=company)]

            for ts in to_process:
                print(f"Need to process: {company}: {ts}")
                features = storage.load(company, ts)
                database.insert_file(company=company, dt=ts, features=features)
                conn.commit()
