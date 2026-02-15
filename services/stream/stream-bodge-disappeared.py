import argparse
import datetime
import os

import psycopg2
from psycopg2.extras import DictCursor

from args import enum_parser
from companies import StreamMembers
from companies import WaterCompany
from stream import FeatureRecord, EventType
from streamdb import Database

if __name__ == '__main__':

    parser = argparse.ArgumentParser(description="Insert fake records for assets that have gone away")
    parser.add_argument("--company", type=enum_parser(WaterCompany), nargs="+", help="company (default: all)")

    args = parser.parse_args()

    db_host = os.environ.get("DB_HOST", "localhost")

    if args.company:
        companies = args.company
    else:
        companies = [w for w in StreamMembers if w != WaterCompany.YorkshireWater] + [WaterCompany.DwrCymru]

    silence_gap = datetime.timedelta(weeks=3)

    with psycopg2.connect(host=db_host, database="gis", user="docker", password="docker",
                          cursor_factory=DictCursor) as conn:

        database = Database(conn)

        for company in companies:

            most_recent_file = database.most_recent_loaded(company=company)

            if most_recent_file is None:
                print(f"{company} has no files loaded")
                continue

            gone_away_date = most_recent_file.file_time - silence_gap

            print(f"{company} Looking for CSOs that were last seen before {gone_away_date}")

            last_seen_by_cso = database.last_seen_cso(company=company)

            gone_away = {cso for cso, when in last_seen_by_cso.items() if when < gone_away_date}

            if gone_away:
                print("Some CSOs have gone away")
                print(gone_away)

                fakes = [
                    FeatureRecord(
                        id=cso_id,
                        company=company,
                        status=EventType.Stop.value,
                        statusStart=when + silence_gap,
                        latestEventStart=when + silence_gap,
                        latestEventEnd=when + silence_gap,
                        lastUpdated=when + silence_gap,
                        lat=0, lon=0, receivingWater='None') for cso_id, when in last_seen_by_cso.items() if
                    cso_id in gone_away]

                fake_file_time = most_recent_file.file_time + datetime.timedelta(milliseconds=1)

                file_ref = database.create_file(company=company, file_time=fake_file_time)

                print(f"{company}: Created fake file {file_ref} at {fake_file_time}")

                database.insert_file_events(file=file_ref, features=fakes)
                database.insert_file_content(file=file_ref, features=fakes)

            conn.commit()
