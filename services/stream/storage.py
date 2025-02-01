import csv
import datetime
import gzip
import os
from dataclasses import asdict, fields, Field
from io import StringIO
from typing import List, Dict, Optional, TypeVar, Callable

import boto3
import botocore.exceptions
import mypy_boto3_s3.service_resource as s3_resources
from botocore.config import Config
from sqlitedict import SqliteDict

from companies import WaterCompany
from stream import FeatureRecord

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


T = TypeVar("T")


class CSVFile[T]:
    def _fields(self) -> List[Field]:
        raise NotImplementedError()

    def to_csv(self, items: List[T]) -> str:
        file = StringIO()
        c = csv.DictWriter(file, fieldnames=[f.name for f in self._fields()])
        c.writeheader()
        for item in items:
            c.writerow(mapout(asdict(item)))
        return file.getvalue()

    def from_csv(self, input: str) -> List[T]:
        file = StringIO(input)
        types = {f.name: f.type for f in self._fields()}
        c = csv.DictReader(file)
        return [FeatureRecord(**mapin(types, r)) for r in c]


class StreamCSV(CSVFile[FeatureRecord]):

    def _fields(self) -> List[Field]:
        return fields(FeatureRecord)


class Storage:

    def available(self, company: WaterCompany) -> List[datetime.datetime]:
        raise NotImplementedError()

    def load(self, company: WaterCompany, dt: datetime.datetime) -> Optional[str]:
        raise NotImplementedError()

    def save(self, company: WaterCompany, dt: datetime.datetime, content: str):
        raise NotImplementedError()


class SqlliteStorage(Storage):
    def __init__(self, delegate: Optional[Storage]):
        self.delegate = delegate
        totp = os.path.expanduser("~/.totp")
        os.makedirs(totp, exist_ok=True)
        self.cache = SqliteDict(
            filename=str(os.path.join(totp, "b2-stream-cache.sqlite")),
            autocommit=True
        )

    def _filename(self, company: WaterCompany, dt: datetime.datetime):
        return f"{company.name}/{dt.strftime('%Y%m%d%H%M%S')}.csv.gz"

    def available(self, company: WaterCompany) -> List[datetime.datetime]:
        keys = [k.split('/')[-1].replace('.csv.gz', '') for k in self.cache.keys() if k.startswith(f"{company.name}/")]
        dates = {datetime.datetime.strptime(k, '%Y%m%d%H%M%S').replace(tzinfo=datetime.UTC) for k in keys}
        if self.delegate is not None:
            dates.update(self.delegate.available(company))

        return sorted(list(dates))

    def load(self, company: WaterCompany, dt: datetime.datetime) -> Optional[str]:
        filename = self._filename(company, dt)
        if filename in self.cache:
            return gzip.decompress(self.cache[filename]).decode()
        if self.delegate is not None:
            content = self.delegate.load(company, dt)
            if content is not None:
                self._put(company, dt, content)
            return content

    def _put(self, company: WaterCompany, dt: datetime.datetime, content: str):
        self.cache[self._filename(company, dt)] = gzip.compress(content.encode())

    def save(self, company: WaterCompany, dt: datetime.datetime, content: str):
        if self.delegate is not None:
            self.delegate.save(company, dt, content)
        self._put(company, dt, content)


class S3Storage(Storage):
    def __init__(self, bucket: s3_resources.Bucket):
        self.bucket = bucket

    def available(self, company: WaterCompany) -> List[datetime.datetime]:
        items = list(self.bucket.objects.filter(Prefix=f"{company.name}/"))
        keys = [i.key.split('/')[-1].replace('.csv.gz', '') for i in items if i.key.endswith(".csv.gz")]
        dates = [datetime.datetime.strptime(k, '%Y%m%d%H%M%S').replace(tzinfo=datetime.UTC) for k in keys]
        return sorted(dates)

    def migration(self, company: WaterCompany):

        items = list(self.bucket.objects.filter(Delimiter='/', Prefix=f"{company.name}/"))
        keys = [i.key.split('/')[-1].replace('.csv.gz', '') for i in items if i.key.endswith(".csv.gz")]
        dates = [datetime.datetime.strptime(k, '%Y%m%d%H%M%S').replace(tzinfo=datetime.UTC) for k in keys]

        commands = []

        for dt in dates:
            old_filename = self._filename_old(company, dt)
            new_filename = self._filename_new(company, dt)
            commands.append(f'mv s3://{self.bucket.name}/{old_filename} s3://{self.bucket.name}/{new_filename}')

        return commands

    def load(self, company: WaterCompany, dt: datetime.datetime) -> Optional[str]:
        print(f"S3 Load: {company} {dt}")
        try:
            new_filename = self._filename_new(company, dt)
            print(f">> Trying {new_filename}")
            resp = self.bucket.Object(key=(new_filename)).get()
        except botocore.exceptions.ClientError as e:
            if e.response['Error']['Code'] in {'NoSuchKey', '404'}:
                old_filename = self._filename_old(company, dt)
                print(f">> Trying {old_filename}")
                resp = self.bucket.Object(key=(old_filename)).get()
            else:
                raise

        return gzip.decompress(resp['Body'].read()).decode()

    def _filename_new(self, company: WaterCompany, dt: datetime.datetime):
        return f"{company.name}/{dt.strftime('%Y/%m/%d/%Y%m%d%H%M%S')}.csv.gz"

    def _filename_old(self, company: WaterCompany, dt: datetime.datetime):
        return f"{company.name}/{dt.strftime('%Y%m%d%H%M%S')}.csv.gz"

    def save(self, company: WaterCompany, dt: datetime.datetime, content: str):
        filename = self._filename_new(company, dt)
        content = gzip.compress(data=content.encode())
        print(f"Writing {filename}")
        self.bucket.put_object(Key=filename, Body=content)


class CSVFileStorage[T]:

    def __init__(self, storage: Storage, csvfile: CSVFile[T]):
        self.storage = storage
        self.csvfile = csvfile

    def available(self, company: WaterCompany) -> List[datetime.datetime]:
        return self.storage.available(company)

    def save(self, company: WaterCompany, dt: datetime.datetime, items: List[T]):
        content = self.csvfile.to_csv(items)
        self.storage.save(company, dt.astimezone(tz=datetime.UTC), content)

    def load(self, company: WaterCompany, dt: datetime.datetime) -> List[T]:
        content = self.storage.load(company, dt)
        if content is None:
            raise FileNotFoundError(f"{company} at {dt}")
        return self.csvfile.from_csv(content)


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
    csvfile = StreamCSV()
    row = csvfile.to_csv([test_item])
    print(row)
    print(csvfile.from_csv(row))


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

    s3_storage = S3Storage(bucket)
    storage = CSVFileStorage(
        SqlliteStorage(delegate=s3_storage),
        StreamCSV()
    )

    company = WaterCompany.ThamesWater
    available = storage.available(company=company)

    loaded = storage.load(company=company, dt=available[0])

    # storage.save(
    #     company=WaterCompany.ThamesWater,
    #     dt=datetime.datetime.now(tz=datetime.UTC),
    #     items=[test_item])

    # print(available)

    # print(loaded)
