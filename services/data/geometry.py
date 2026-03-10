#!/usr/bin/env python3
import argparse
import os

from utils import iter_row, kebabcase, connect

MYDIR = os.path.dirname(__file__)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="generate constituency shape files")
    parser.add_argument("sql", help="sql to run")
    parser.add_argument("output", help="output directory")

    args = parser.parse_args()

    output_dir = args.output

    os.makedirs(output_dir, exist_ok=True)

    pool = connect("localhost")

    with pool.connection() as conn:

        with open(args.sql) as f:
            sql = f.read()

        with conn.cursor() as cursor:
            cursor.execute(sql)

            columns = [desc[0] for desc in cursor.description]

            for row in iter_row(cursor, 20):
                (name, points_original, points_reduced, geom) = row["name"], row["points_original"], row["points_reduced"], row["geometry"]
                print(f"{name} orig={points_original} reduced={points_reduced}")

                with open(os.path.join(output_dir, f"{kebabcase(name)}.json"), "w") as fp:
                    fp.write(geom)
