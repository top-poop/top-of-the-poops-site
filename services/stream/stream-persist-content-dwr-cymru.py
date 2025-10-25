import argparse
import datetime
import os
from collections import Counter
from dataclasses import replace
from datetime import timezone

import psycopg2
from psycopg2.extras import DictCursor

from secret import env
from companies import WaterCompany
from storage import DwrCymruCSV
from stream import DwrCymruRecord
from storage import b2_service, CSVFileStorage, SqlliteStorage, S3Storage
from stream import FeatureRecord, EventType
from streamdb import Database

if __name__ == '__main__':

    parser = argparse.ArgumentParser(description="Attempt to parse events from stream status files - DwyCymru")

    args = parser.parse_args()

    s3 = b2_service(
        env("AWS_ACCESS_KEY_ID", "s3_key_id"),
        env("AWS_SECRET_ACCESS_KEY", "s3_secret_key")
    )
    bucket = s3.Bucket(env("STREAM_BUCKET_NAME", "stream_bucket_name"))

    storage = CSVFileStorage(
        SqlliteStorage(delegate=S3Storage(bucket)),
        DwrCymruCSV()
    )

    db_host = os.environ.get("DB_HOST", "localhost")

    company = WaterCompany.DwrCymru

    with psycopg2.connect(host=db_host, database="gis", user="docker", password="docker",
                          cursor_factory=DictCursor) as conn:

        database = Database(conn)

        most_recent_file = database.most_recent_loaded(company=company)

        if most_recent_file is None:
            since = datetime.datetime.combine(
                date=datetime.date.fromisoformat("2024-12-01"),
                time=datetime.datetime.min.time(),
                tzinfo=timezone.utc
            )
        else:
            since = most_recent_file.file_time

        to_process = [ts for ts in storage.available(company=company, since=since)]

        print(f"Loading most recent records for {company}")

        most_recent = database.most_recent_records(company=company)
        most_recent_by_id = {x.id: x for x in most_recent}

        for ts in to_process:
            print(f"Need to process: {company}: {ts}")

            file_ref = database.create_file(company=company, file_time=ts)

            features = storage.load(company, ts)

            print(f"File {file_ref.company} {file_ref.file_id}- Records in file {len(features)}, valid {len(features)}")

            ## Now we need to filter out any duplicates
            counter = Counter()


            def not_a_duplicate(f: DwrCymruRecord):
                counter.update([f.GlobalID])
                return counter[f.GlobalID] == 1


            unique_features = [f for f in features if not_a_duplicate(f)]

            if len(unique_features):
                if counter.most_common(1)[0][1] > 1:
                    print(counter.most_common(5))

            print(f"From {len(features)} events, unique is {len(unique_features)}")

            ids = database.load_ids(company=company)


            def want(f: FeatureRecord):
                if f.id not in most_recent_by_id:
                    return True

                r = most_recent_by_id[f.id]
                new_feature = (EventType(int(f.status)), f.statusStart, f.latestEventStart, f.latestEventEnd)
                existing_feature = (r.status, r.statusStart, r.latestEventStart, r.latestEventEnd)
                return new_feature != existing_feature


            converted_features = [d.as_feature_record() for d in features]

            wanted_features = [f for f in converted_features if want(f)]
            print(f"Got {len(unique_features)} events, want {len(wanted_features)}")

            database.insert_file_events(file=file_ref, features=wanted_features)
            database.insert_file_content(file=file_ref, features=converted_features)

            most_recent_by_id.update({w.id: replace(w, status=EventType(int(w.status))) for w in wanted_features})
            conn.commit()
