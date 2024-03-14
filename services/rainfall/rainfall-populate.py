import argparse
import datetime
import os
import pathlib
from collections import defaultdict

import psycopg2
from psycopg2.extras import execute_values
import requests as requests

from ea import EnvironmentAgencyAPI
from psy import select_one

class DateArgAction(argparse.Action):
    def __call__(self, parser, namespace, values, option_string=None):
        setattr(namespace, self.dest, datetime.date.fromisoformat(values))


def process_date(connection, api: EnvironmentAgencyAPI, on_date, known_stations):
    print(f"Date - {on_date} ..", end="")
    warned = defaultdict(lambda: 0)
    with connection.cursor() as cursor:
        cursor.execute("delete from rainfall_readings where date_trunc('day', date_time) = %s", (on_date,))

        events = api.readings(on_date)

        rows = [(event.station_reference, event.date_time, event.mm) for event in events if event.station_reference in known_stations]

        print(f"Inserting {len(rows)} records")

        execute_values(cur=cursor,
                       sql="insert into rainfall_readings(station_id, date_time, reading_mm) values %s",
                       argslist=rows
                       )

        connection.commit()


if __name__ == "__main__":

    parser = argparse.ArgumentParser(description="Populate rainfall from Environment Agency")
    parser.add_argument("--update", action="store_true", help="Run in update mode")
    parser.add_argument("--reset", action="store_true", help="Delete database rows and repopulate")
    parser.add_argument("--date", action=DateArgAction, help="load a single date yyyy-mm-dd")
    parser.add_argument("--to", action=DateArgAction, help="to date yyyy-mm-dd (when using --date)")

    args = parser.parse_args()

    if (not args.update) and (not args.reset) and  args.date is None:
        raise ValueError("Need one of --update --reset or --date <date>")

    cache_dir = pathlib.Path("~/.gis-cache").expanduser()
    cache_dir.mkdir(exist_ok=True)
    api = EnvironmentAgencyAPI(cache_dir)

    start_date = datetime.date(2022, 12, 1)
    a_day = datetime.timedelta(days=1)
    end_date = datetime.date.today() - a_day

    db_host = os.environ.get("DB_HOST", "localhost")

    with psycopg2.connect(host=db_host, database="gis", user="docker", password="docker") as conn:

        if args.update:
            d: datetime.datetime = select_one(conn, "select max(date_trunc('day', date_time)) from rainfall_readings")[0]
            if d is not None:
                start_date = d.date() + a_day

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
        if start_date == end_date:
            print("Nothing to do")
            exit(0)

        current_date = start_date

        try:
            stations = api.stations()

            known_stations = set()

            with conn.cursor() as cursor:
                for station in stations:
                    known_stations.add(station.station_reference)
                    cursor.execute(
                        "insert into rainfall_stations(station_id, lat, lon) values (%s, %s, %s) on conflict do nothing",
                        (station.station_reference, station.lat, station.lon)
                    )
                cursor.execute("update rainfall_stations set point = ST_SETSRID(ST_MakePoint(lon, lat), 4326) where point is null")
            conn.commit()


            while current_date < end_date:
                try:
                    process_date(conn, api, current_date, known_stations)
                except requests.exceptions.HTTPError as e:
                    print(f"Response: {e.response.text}")
                current_date = current_date + a_day
                # sleep(2)
        except requests.exceptions.HTTPError as e:
            print(f"API Failed: {e}")
            print(f"Request URL= {e.response.url}")
            print(f"Response: {e.response.text}")
            raise
        finally:
            print("Updating views...")
            with conn.cursor() as cursor:
                cursor.execute("refresh materialized view rainfall_daily_consitituency")
