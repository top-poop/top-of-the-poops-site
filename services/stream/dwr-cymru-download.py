import argparse
import datetime

from companies import WaterCompany
from secret import env
from storage import DwrCymruCSV
from storage import b2_service, CSVFileStorage, SqlliteStorage, S3Storage
from stream import DwrCymruAPI

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Download current status from Dwr Cymru non-api")
    args = parser.parse_args()

    s3 = b2_service(
        env("AWS_ACCESS_KEY_ID", "s3_key_id"),
        env("AWS_SECRET_ACCESS_KEY", "s3_secret_key")
    )
    bucket = s3.Bucket(env("STREAM_BUCKET_NAME", "stream_bucket_name"))

    company = WaterCompany.DwrCymru

    storage = CSVFileStorage(
        SqlliteStorage(delegate=S3Storage(bucket)),
        DwrCymruCSV()
    )

    print(f"Loading {company}")
    api = DwrCymruAPI()
    features = api.features()
    storage.save(company=company, dt=datetime.datetime.now(), items=features)
