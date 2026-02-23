#!/usr/bin/env python
import argparse
import contextlib
import logging
import os
import sys

import psy
from psy import *
import osgb

T = TypeVar('T')

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s.%(msecs)03dZ %(levelname)s [%(name)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)

logger = logging.getLogger(__name__)


@contextlib.contextmanager
def smart_open(filename=None):
    if filename and filename != '-':
        fh = open(filename, 'w')
    else:
        fh = sys.stdout

    try:
        yield fh
    finally:
        if fh is not sys.stdout:
            fh.close()


select_sql = """
             select stream_id, lat, lon
             from stream_cso sc
                      left join stream_cso_grid sg on sc.stream_cso_id = sg.stream_cso_id
             where sg.grid_reference is null
             """

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="update grid references from new locations from stream")

    db_host = os.environ.get("DB_HOST", "localhost")

    args = parser.parse_args()

    update_materialised_views = False

    pool = psy.connect(db_host)

    with pool.connection() as conn:
        for cso_id, lat, lon in select_many(conn, select_sql, f=lambda row: (row["stream_id"], row["lat"], row["lon"])):
            try:
                result = osgb.lonlat_to_osgb(lon, lat, digits=5, formatted=False)
                update_materialised_views = True
                with conn.cursor() as cursor:
                    cursor.execute(
                        """
                        insert into grid_references(grid_reference, lat, lon, point, pcon24nm)
                        values (%(ref)s,
                                %(lat)s,
                                %(lon)s,
                                ST_SETSRID(ST_MakePoint(%(lon)s, %(lat)s), 4326),
                                (select con.pcon24nm
                                 from pcon_july_2024_uk_bfc con
                                 order by
                                     ST_SETSRID(ST_MakePoint(%(lon)s, %(lat)s), 4326) < - > con.wkb_geometry limit 1) )
                        on conflict (grid_reference) do nothing
                        """,
                        {
                            "lat": lat,
                            "lon": lon,
                            "ref": result
                        }
                    )

                logger.info(f"{cso_id}, {result}, {lat}, {lon}")
            except osgb.gridder.FarFarAwayError:
                logger.info(f"{cso_id}: {lon}/{lat} too far away")

        conn.commit()

        if update_materialised_views:
            logger.info("Updating views...")
            with conn.cursor() as cursor:
                cursor.execute("refresh materialized view stream_cso_grid")
