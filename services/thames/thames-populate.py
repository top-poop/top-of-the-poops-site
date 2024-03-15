#!/usr/bin/env python3

import argparse
import datetime
import os
import psycopg2
import requests as requests
from time import sleep
from typing import Optional

from psy import select_one
from thames import TWApi, Credential


class DateArgAction(argparse.Action):
    def __call__(self, parser, namespace, values, option_string=None):
        setattr(namespace, self.dest, datetime.date.fromisoformat(values))


def process_date(connection, on_date):
    print(f"Date - {on_date} ..", end="")
    with connection.cursor() as cursor:

        events = api.events(on_date)
        print(f". {len(events)}")

        if len(events) > 0:
            cursor.execute("delete from events_thames where date_trunc('day', date_time) = %s", (on_date,))

            for event in events:
                row = (event.location_name, event.permit_number, event.location_grid_ref, event.x, event.y,
                       event.alert_type, event.date_time)
                cursor.execute(
                    "insert into events_thames(location_name, permit_number, location_grid_reference, "
                    "x, y, alert_type, date_time) values (%s, %s, %s, %s, %s, %s, %s)",
                    row
                )
        connection.commit()


def secret_value(name) -> Optional[str]:
    path = f"/run/secrets/{name}"
    if os.path.exists(path):
        with open(path) as f:
            return f.readline().strip()
    return None


def env(name: str, secret_name: str) -> str:
    value = os.environ.get(name)
    if value is None:
        value = secret_value(secret_name)
    if value is None:
        raise IOError(f"Can't get env:{name} or secret:{secret_name}")
    return value


if __name__ == "__main__":

    parser = argparse.ArgumentParser(description="Populate events from Thames Water")
    parser.add_argument("--update", action="store_true", help="Run in update mode")
    parser.add_argument("--reset", action="store_true", help="Delete everything and redownload")
    parser.add_argument("--date", action=DateArgAction, help="load a single date yyyy-mm-dd")
    parser.add_argument("--to", action=DateArgAction, help="to date yyyy-mm-dd (when using --date)")

    args = parser.parse_args()

    if (not args.update) and (not args.reset) and args.date is None:
        raise ValueError("Need one of --update --reset or --date <date>")

    api = TWApi(
        Credential(
            env("TW_CLIENT_ID", "tw_client_id"),
            env("TW_CLIENT_SECRET", "tw_client_secret")
        )
    )

    start_date = datetime.date(2022, 12, 1)
    end_date = datetime.date.today()
    a_day = datetime.timedelta(days=1)

    db_host = os.environ.get("DB_HOST", "localhost")

    with psycopg2.connect(host=db_host, database="gis", user="docker", password="docker") as conn:

        if args.update:
            print("Getting last date....")
            d: datetime.datetime = select_one(conn, "select max(date_time) from events_thames")[0]
            if d is not None:
                start_date = d.date()

        if args.reset:
            pass

        if args.date:
            start_date = args.date
            end_date = start_date + a_day
            if args.to:
                end_date = args.to
            if end_date < start_date:
                raise ValueError("End Date must be before start date")

        print(f"Start date is {start_date}, end date is {end_date}")

        current_date = start_date

        try:
            while current_date <= end_date:
                process_date(conn, current_date)
                current_date = current_date + a_day
                sleep(2)
        except requests.exceptions.HTTPError as e:
            print(f"API Failed: {e}")
            print(f"Request URL= {e.response.url}")
            print(f"Response: {e.response.text}")
            raise
