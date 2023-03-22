#!/usr/bin/env python3
import argparse
import psycopg2
from utils import smart_open

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

            with smart_open(args.output) as fp:
                for row in cursor.fetchall():
                    fp.write("".join([str(c) for c in row]))
