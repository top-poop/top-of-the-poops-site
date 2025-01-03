import csv
import datetime
import gzip
import os
from dataclasses import asdict, fields
from io import StringIO
from typing import List, Dict, Optional, TypeVar, Callable

import boto3
import mypy_boto3_s3.service_resource as s3_resources
from botocore.config import Config
from sqlitedict import SqliteDict

from companies import WaterCompany
from stream import FeatureRecord

csv_fields = ['lastUpdated', 'id', 'status', 'statusStart', 'latestEventStart',
              'latestEventEnd', 'company', 'lat', 'lon', 'receivingWater']

K = TypeVar("K")
V = TypeVar("V")


class KeyDefaultDict(dict[K, V]):
    def __init__(self, default_factory: Callable[[K], V]):
        super().__init__()
        self.default_factory = default_factory

    def __missing__(self, key: K) -> V:
        if self.default_factory is None:
            raise KeyError(key)
        else:
            ret = self[key] = self.default_factory(key)
            return ret


def serialize_field(value):
    if isinstance(value, datetime.datetime):
        return value.isoformat()
    return value


dt_cache = KeyDefaultDict(lambda x: datetime.datetime.fromisoformat(x))


def deserialize_field(field_type, value):
    if field_type == Optional[datetime.datetime]:
        if value == "":
            return None
        return dt_cache[value]
    if field_type == datetime.datetime:
        return dt_cache[value]
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
        totp = os.path.expanduser("~/.totp")
        os.makedirs(totp, exist_ok=True)
        self.cache = SqliteDict(
            filename=str(os.path.join(totp, "b2-stream-cache.sqlite")),
            autocommit=True
        )

    def available(self, company: WaterCompany) -> List[datetime.datetime]:
        items = self.bucket.objects.filter(Delimiter="/", Prefix=f"{company.name}/")
        keys = [i.key.split('/')[1].replace('.csv.gz', '') for i in items if i.key.endswith(".csv.gz")]
        dates = [datetime.datetime.strptime(k, '%Y%m%d%H%M%S').replace(tzinfo=datetime.UTC) for k in keys]
        return sorted(dates)

    def _filename(self, company: WaterCompany, dt: datetime.datetime):
        return f"{company.name}/{dt.strftime('%Y%m%d%H%M%S')}.csv.gz"

    def save(self, company: WaterCompany, dt: datetime.datetime, items: List[FeatureRecord]):
        filename = self._filename(company, dt.astimezone(tz=datetime.UTC))
        content = gzip.compress(data=to_csv(items).encode())
        self.bucket.put_object(
            Key=filename,
            Body=(content)
        )
        self.cache[filename] = content

    def load(self, company: WaterCompany, dt: datetime.datetime) -> List[FeatureRecord]:
        filename = self._filename(company, dt)
        if filename in self.cache:
            content = self.cache[filename]
        else:
            resp = self.bucket.Object(key=filename).get()
            content = resp['Body'].read()
            self.cache[filename] = content

        return from_csv(gzip.decompress(content).decode())


test_item = FeatureRecord(
    id="ID",
    status="0",
    company='Thames Water',
    statusStart=None,
    latestEventStart=datetime.datetime.now(tz=datetime.UTC),
    latestEventEnd=datetime.datetime.now(tz=datetime.UTC),
    lastUpdated=datetime.datetime.now(tz=datetime.UTC),
    lat=123.45,
    lon=43.210,
    receivingWater='who cares'
)


def test_round_trip():
    print(from_csv(to_csv([test_item])))


def b2_service(aws_access_key_id: str, aws_secret_access_key: str):
    return boto3.resource(service_name='s3',
                          endpoint_url="https://s3.us-west-000.backblazeb2.com",
                          aws_access_key_id=aws_access_key_id,
                          aws_secret_access_key=aws_secret_access_key,
                          config=Config(
                              signature_version='s3v4',
                          ))


if __name__ == "__main__":
    s3 = b2_service(os.environ["AWS_ACCESS_KEY_ID"], os.environ["AWS_SECRET_ACCESS_KEY"])
    bucket = s3.Bucket(os.environ["STREAM_BUCKET_NAME"])
    storage = Storage(bucket)

    storage.save(
        company=WaterCompany.ThamesWater,
        dt=datetime.datetime.now(tz=datetime.UTC),
        items=[test_item])

    company = WaterCompany.ThamesWater
    available = storage.available(company=company)
    print(available)

    loaded = storage.load(company=company, dt=available[0])

    print(loaded)
