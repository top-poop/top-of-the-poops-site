import argparse
import datetime
import os
from collections import Counter
from dataclasses import replace
from datetime import timezone
from typing import List

import logging

import psy
from secret import env
from companies import WaterCompany
from storage import DwrCymruCSV, garage_service
from stream import DwrCymruRecord
from storage import b2_service, CSVFileStorage, SqlliteStorage, S3Storage
from stream import FeatureRecord, EventType
from streamdb import Database


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s.%(msecs)03dZ %(levelname)s [%(name)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)

logger = logging.getLogger(__name__)


def insert_stream_cso_lookup(conn, features: List[DwrCymruRecord]):
    # stream_id, stream_id_old, site_name_consent, site_name_wasc, wfd_waterbody_id, receiving_water
    logger.info(f"Ensuring {len(features)} CSOs inserted")
    with conn.cursor() as cursor:
        for feature in features:
            cursor.execute("""
                         insert into stream_lookup (stream_id, stream_id_old, site_name_consent, site_name_wasc, wfd_waterbody_id, receiving_water)
                         values( %(stream_id)s, null, %(site_name)s, %(site_name)s, null, %(receiving_water)s)
                        on conflict do nothing
                         """, {
                "stream_id": feature.assetid,
                "site_name": feature.assetName,
                "receiving_water": feature.Receiving_Water
            })


if __name__ == '__main__':

    parser = argparse.ArgumentParser(description="Attempt to parse events from stream status files - DwyCymru")
    parser.add_argument("--garage", action="store_true")
    args = parser.parse_args()

    if args.garage:
        s3 = garage_service(
            env("GARAGE_ACCESS_KEY_ID", "s3_key_id"),
            env("GARAGE_SECRET_ACCESS_KEY", "s3_secret_key")
        )
        bucket = s3.Bucket(env("GARAGE_BUCKET_NAME", "stream_bucket_name"))
    else:
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

    pool = psy.connect(db_host)

    with pool.connection() as conn:

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


        logger.info(f"Loading most recent records for {company}")

        most_recent = database.most_recent_records(company=company)
        most_recent_by_id = {x.id: x for x in most_recent}

        for ts in storage.available(company=company, since=since):
            print(f"Need to process: {company}: {ts}")

            file_ref = database.create_file(company=company, file_time=ts)

            features = storage.load(company, ts)

            logger.info(f"File {file_ref.company} {file_ref.file_id}- Records in file {len(features)}, valid {len(features)}")

            ## Now we need to filter out any duplicates
            counter = Counter()

            def not_a_duplicate(f: DwrCymruRecord):
                counter.update([f.assetid])
                return counter[f.assetid] == 1

            unique_features = [f for f in features if not_a_duplicate(f)]

            if len(unique_features):
                if counter.most_common(1)[0][1] > 1:
                    print(counter.most_common(5))

            logger.info(f"From {len(features)} events, unique is {len(unique_features)}")

            ids = database.load_ids(company=company)

            def want(f: FeatureRecord):
                if f.id not in most_recent_by_id:
                    return True

                r = most_recent_by_id[f.id]
                new_feature = (EventType(int(f.status)), f.statusStart, f.latestEventStart, f.latestEventEnd)
                existing_feature = (r.status, r.statusStart, r.latestEventStart, r.latestEventEnd)
                return new_feature != existing_feature

            converted_features = [d.as_feature_record() for d in unique_features]

            wanted_features = [f for f in converted_features if want(f)]
            logger.info(f"Got {len(unique_features)} events, want {len(wanted_features)}")

            database.insert_file_events(file=file_ref, features=wanted_features)
            database.insert_file_content(file=file_ref, features=converted_features)

            insert_stream_cso_lookup(conn, unique_features)

            most_recent_by_id.update({w.id: replace(w, status=EventType(int(w.status))) for w in wanted_features})
            conn.commit()
