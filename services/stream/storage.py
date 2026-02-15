import csv
import datetime
import gzip
import itertools
import os
from dataclasses import asdict, fields, Field
from io import StringIO
from typing import List, Dict, Optional, TypeVar, Callable, get_origin, Union, get_args, Tuple, Any, Generator

import boto3
import botocore.exceptions
import mypy_boto3_s3.service_resource as s3_resources
from botocore.config import Config
from sqlitedict import SqliteDict

from companies import WaterCompany
from stream import DwrCymruRecord, FeatureRecord

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


def is_optional(field_type) -> Tuple[bool, Any]:
    origin = get_origin(field_type)
    if origin is Union:
        args = get_args(field_type)
        if len(args) != 2:
            raise ValueError(f"Can't handle Union Type {field_type}")
        return type(None) in args, args[0]
    return False, field_type


def deserialize_field(field_type, value:str):
    value = value.strip()
    opt, t = is_optional(field_type)
    if opt:
        if value == "":
            return None
        if t == datetime.datetime:
            return dt_cache[value]
    if t == datetime.datetime:
        return dt_cache[value]
    return t(value)


def mapout(d: Dict) -> Dict:
    return {k: serialize_field(v) for (k, v) in d.items()}


def mapin(t: Dict, d: Dict) -> Dict:
    return {k: deserialize_field(t[k], v) for (k, v) in d.items()}


T = TypeVar("T")


class CSVFile[T]:
    def _fields(self) -> List[Field]:
        raise NotImplementedError()

    def _construct(self, **kwargs) -> T:
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
        return [self._construct(**mapin(types, r)) for r in c]


class StreamCSV(CSVFile[FeatureRecord]):

    def _fields(self) -> List[Field]:
        return fields(FeatureRecord)

    def _construct(self, **kwargs) -> T:
        return FeatureRecord(**kwargs)


class DwrCymruCSV(CSVFile[DwrCymruRecord]):
    def _fields(self) -> List[Field]:
        return fields(DwrCymruRecord)

    def _construct(self, **kwargs) -> T:
        return DwrCymruRecord(**kwargs)


class Storage:

    def available(self, company: WaterCompany, since: datetime.datetime) -> Generator[datetime.datetime, Any, None]:
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

    def available(self, company: WaterCompany, since: datetime.datetime) -> Generator[datetime.datetime, Any, None]:

        if self.delegate is not None:
            yield from self.delegate.available(company, since)
        else:
            keys = [k.split('/')[-1].replace('.csv.gz', '') for k in self.cache.keys() if k.startswith(f"{company.name}/")]
            all_dates = {datetime.datetime.strptime(k, '%Y%m%d%H%M%S').replace(tzinfo=datetime.UTC) for k in keys}
            dates = {d for d in all_dates if d > since}
            for file in sorted(list(dates)):
                yield file

    def load(self, company: WaterCompany, dt: datetime.datetime) -> Optional[str]:
        filename = self._filename(company, dt)
        if filename in self.cache:
            print(f"Cache Load: {company} {dt}")
            return gzip.decompress(self.cache[filename]).decode()
        if self.delegate is not None:
            content = self.delegate.load(company, dt)
            if content is not None:
                self._put(company, dt, content)
            return content
        raise FileNotFoundError(f"{company}: can't load {dt} - no such file")

    def _put(self, company: WaterCompany, dt: datetime.datetime, content: str):
        self.cache[self._filename(company, dt)] = gzip.compress(content.encode())

    def save(self, company: WaterCompany, dt: datetime.datetime, content: str):
        if self.delegate is not None:
            self.delegate.save(company, dt, content)
        self._put(company, dt, content)


class S3Storage(Storage):
    def __init__(self, bucket: s3_resources.Bucket):
        self.bucket = bucket

    def _files_on(self, company: WaterCompany, date: datetime.date) -> List[datetime.datetime]:
        print(f">> Finding files for {company} on {date}")
        folder = date.strftime("%Y/%m/%d")
        items = list(self.bucket.objects.filter(Prefix=f"{company.name}/{folder}/"))
        keys = [i.key.split('/')[-1].replace('.csv.gz', '') for i in items if i.key.endswith(".csv.gz")]
        dates = [datetime.datetime.strptime(k, '%Y%m%d%H%M%S').replace(tzinfo=datetime.UTC) for k in keys]
        return dates

    def available(self, company: WaterCompany, since: datetime.datetime) -> Generator[datetime.datetime, Any, None]:

        start_date = since.date()
        end_date = datetime.date.today()

        dates = [start_date + datetime.timedelta(days=x) for x in range((end_date - start_date).days + 1)]

        for date in dates:
            files_on_date = [d for d in self._files_on(company=company, date=date) if d > since]
            for file in files_on_date:
                yield file



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

    def available(self, company: WaterCompany, since: datetime.datetime) -> List[datetime.datetime]:
        return self.storage.available(company, since=since)

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


def garage_service(aws_access_key_id: str, aws_secret_access_key: str):
    return boto3.resource(service_name='s3',
                          region_name='garage',
                          endpoint_url="http://localhost:3900",
                          aws_access_key_id=aws_access_key_id,
                          aws_secret_access_key=aws_secret_access_key,
                          config=Config(
                              signature_version='s3v4',
                          ))


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
