import argparse
import datetime

from args import enum_parser
from companies import StreamMembers
from companies import WaterCompany
from secret import env
from storage import b2_service, CSVFileStorage, SqlliteStorage, StreamCSV, S3Storage
from stream import StreamAPI

if __name__ == "__main__":

    parser = argparse.ArgumentParser(description="Download current status from horrible stream api")
    parser.add_argument("--company", type=enum_parser(WaterCompany), nargs="+", help="company (default: all)")

    args = parser.parse_args()

    if args.company is None:
        companies = StreamMembers
    else:
        companies = args.company

    s3 = b2_service(
        env("AWS_ACCESS_KEY_ID", "s3_key_id"),
        env("AWS_SECRET_ACCESS_KEY", "s3_secret_key")
    )
    bucket = s3.Bucket(env("STREAM_BUCKET_NAME", "stream_bucket_name"))

    storage = CSVFileStorage(
        SqlliteStorage(delegate=S3Storage(bucket)),
        StreamCSV()
    )

    for company in companies:
        print(f"Loading {company}")
        api = StreamAPI(company=company)
        features = api.features()
        storage.save(company=company, dt=datetime.datetime.now(), items=features)
