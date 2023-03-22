#!/usr/bin/env python3
import argparse
import datetime
import json
import psycopg2
from decimal import Decimal
from typing import Any

from utils import smart_open

class MultipleJsonEncoders:
    """
    Combine multiple JSON encoders
    """

    def __init__(self, *encoders):
        self.encoders = encoders
        self.args = ()
        self.kwargs = {}

    def default(self, obj):
        for encoder in self.encoders:
            try:
                return encoder(*self.args, **self.kwargs).default(obj)
            except TypeError:
                pass
        raise TypeError(f'Object of type {obj.__class__.__name__} is not JSON serializable')

    def __call__(self, *args, **kwargs):
        self.args = args
        self.kwargs = kwargs
        enc = json.JSONEncoder(*args, **kwargs)
        enc.default = self.default
        return enc


class DecimalEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, Decimal):
            return float(str(obj))
        return json.JSONEncoder.default(self, obj)


class DateEncoder(json.JSONEncoder):

    def default(self, obj: Any) -> Any:
        if isinstance(obj, datetime.date):
            return obj.isoformat()
        return json.JSONEncoder.default(self, obj)


class TimeDeltaEncoder(json.JSONEncoder):
    def default(self, obj: Any) -> Any:
        if isinstance(obj, datetime.timedelta):
            return obj.total_seconds()
        return json.JSONEncoder.default(self, obj)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="run sql script and make json")
    parser.add_argument("script", default=".", help="sql to run")
    parser.add_argument("output", default="-", nargs="?", help="output file")

    args = parser.parse_args()

    with psycopg2.connect(host="localhost", database="gis", user="docker", password="docker") as conn:

        with open(args.script) as s:
            script = s.read()

        with conn.cursor() as cursor:
            cursor.execute(script)

            columns = [desc[0] for desc in cursor.description]

            result = []

            for row in cursor.fetchall():
                result.append(dict(zip(columns, row)))

            if len(result) == 1:
                result = result[0]

            with smart_open(args.output) as fp:
                json.dump(result, cls=MultipleJsonEncoders(DecimalEncoder, DateEncoder, TimeDeltaEncoder), indent=2,
                          fp=fp)
