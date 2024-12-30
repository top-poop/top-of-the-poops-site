import csv
import datetime
import os
from dataclasses import asdict, fields
from io import StringIO
from typing import List, Dict, Optional

import boto3
import mypy_boto3_s3.service_resource as s3_resources
from botocore.config import Config
from mypy_boto3_s3.service_resource import ObjectSummary

from companies import WaterCompany
from stream import FeatureRecord

csv_fields = ['lastUpdated', 'id', 'status', 'statusStart', 'latestEventStart',
              'latestEventEnd', 'company', 'lat', 'lon', 'receivingWater']

DATETIME_FORMAT = "%Y-%m-%d %H:%M:%S"


def serialize_field(value):
    if isinstance(value, datetime.datetime):
        return value.strftime(DATETIME_FORMAT)
    return value


def deserialize_field(field_type, value):
    if field_type == Optional[datetime.datetime]:
        if value == "":
            return None
        return datetime.datetime.strptime(value, DATETIME_FORMAT)
    if field_type == datetime.datetime:
        return datetime.datetime.strptime(value, DATETIME_FORMAT)
    return field_type(value)


def mapout(d: Dict) -> Dict:
    return {k: serialize_field(v) for (k, v) in d.items()}


def mapin(t: Dict, d: Dict) -> Dict:
    return {k: deserialize_field(t[k], v) for (k, v) in d.items()}


def to_csv(items: List[FeatureRecord]) -> str:
    file = StringIO()
    c = csv.DictWriter(file, fieldnames=csv_fields)
    c.writeheader()
    for item in items:
        c.writerow(mapout(asdict(item)))
    return file.getvalue()


def from_csv(text: str) -> List[FeatureRecord]:
    file = StringIO(text)
    types = {f.name: f.type for f in fields(FeatureRecord)}
    c = csv.DictReader(file)
    return [FeatureRecord(**mapin(types, r)) for r in c]


class Storage:

    def __init__(self, bucket: s3_resources.Bucket):
        self.bucket = bucket

    def available(self, company: WaterCompany) -> List[datetime.datetime]:
        items = self.bucket.objects.filter(Delimiter="/", Prefix=f"{company.name}/")
        keys = [i.key.split('/')[1].replace('.csv','') for i in items]
        return [datetime.datetime.strptime(k, '%Y%m%d%H%M%S') for k in keys]

    def _filename(self, company: WaterCompany, dt: datetime.datetime):
        return f"{company.name}/{dt.strftime('%Y%m%d%H%M%S')}.csv"

    def save(self, company: WaterCompany, dt: datetime.datetime, items: List[FeatureRecord]):
        self.bucket.put_object(Key=self._filename(company, dt), Body=(to_csv(items)))

    def load(self, company: WaterCompany, dt: datetime.datetime) -> List[FeatureRecord]:
        resp = self.bucket.Object(key=self._filename(company, dt)).get()
        content = resp['Body'].read().decode('utf-8')
        return from_csv(content)


test_item = FeatureRecord(
    id="ID",
    status="0",
    company='Thames Water',
    statusStart=None,
    latestEventStart=datetime.datetime.now(),
    latestEventEnd=datetime.datetime.now(),
    lastUpdated=datetime.datetime.now(),
    lat=123.45,
    lon=43.210,
    receivingWater='who cares'
)


def test_round_trip():
    print(from_csv(to_csv([test_item])))


if __name__ == "__main__":
    s3 = boto3.resource(service_name='s3',
                        endpoint_url="https://s3.us-west-000.backblazeb2.com",
                        aws_access_key_id=os.environ["AWS_ACCESS_KEY_ID"],
                        aws_secret_access_key=os.environ["AWS_SECRET_ACCESS_KEY"],
                        config=Config(
                            signature_version='s3v4',
                        ))
    bucket = s3.Bucket(os.environ["STREAM_BUCKET_NAME"])
    storage = Storage(bucket)

    # storage.save(
    #     company=WaterCompany.ThamesWater,
    #     dt=datetime.datetime.now(),
    #     items=[test_item])

    company = WaterCompany.ThamesWater
    available = storage.available(company=company)
    print(available)

    loaded = storage.load(company=company, dt=available[0])

    print(loaded)
