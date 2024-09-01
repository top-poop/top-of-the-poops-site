#!/usr/bin/env python3

import argparse
import dataclasses
import datetime
import itertools
import json
import math
import os
import pathlib
import psycopg2

import utils
from lib import psy
from lib.encoder import MultipleJsonEncoders, DateTimeEncoder, TimeDeltaMinutesEncoder

_ZERO = datetime.timedelta(seconds=0)


@dataclasses.dataclass
class Bucket:
    online: datetime.timedelta = _ZERO
    offline: datetime.timedelta = _ZERO
    overflowing: datetime.timedelta = _ZERO
    unknown: datetime.timedelta = _ZERO
    potentially_overflowing: datetime.timedelta = _ZERO

    def total(self):
        return self.online + self.offline + self.overflowing + self.unknown + self.potentially_overflowing


class Summariser:

    def _key(self, c, td: datetime.timedelta):
        s = int(math.ceil((td.total_seconds() / 3600) / 4) * 4)
        return f"{c}-{s}"

    def summarise(self, total: Bucket):

        if total.overflowing > _ZERO:
            return self._key("o", total.overflowing)
        elif total.potentially_overflowing > _ZERO:
            return self._key("p", total.potentially_overflowing)
        elif total.offline > _ZERO:
            return self._key("z", total.offline)
        elif total.unknown > _ZERO:
            return self._key("u", total.unknown)
        elif total.online > _ZERO:
            return self._key("a", total.online)


@dataclasses.dataclass
class Rainfall:
    r_min: float
    r_max: float
    r_pct_50: float
    r_pct_75: float
    stations: int


def rainfall_by_constituency(connection, constituency):
    return {
        r[0]: r[1]
        for r in psy.select_many(connection,
                                 sql="select date, min, avg, max, pct_75, count from rainfall_daily_consitituency where pcon24nm = %s",
                                 params=(constituency,),
                                 f=lambda r: (
                                     r["date"], Rainfall(
                                         r_min=float(r["min"]),
                                         r_pct_50=float(r["avg"]),
                                         r_max=float(r["max"]),
                                         r_pct_75=float(r["pct_75"]),
                                         stations=r["count"]))
                                 )
    }


sql = """
select
       g.pcon24nm as constituency,
       permit_id,
       st.date,
       online,
       offline,
       overflowing,
       unknown,
       potentially_overflowing,
       discharge_site_name
from summary_thames st
         join consents_unique_view c on st.permit_id = c.permit_number
         join grid_references g on c.effluent_grid_ref = g.grid_reference
         order by g.pcon24nm, st.date
"""


def thames_summary(connection):
    return itertools.groupby(psy.select_many(
        connection=connection,
        sql=sql,
        f=lambda m: m | {"bucket": Bucket(
            online=m["online"],
            offline=m["offline"],
            overflowing=m["overflowing"],
            potentially_overflowing=m["potentially_overflowing"],
            unknown=m["unknown"]
        )}
    ), key=lambda it: it["constituency"])


if __name__ == "__main__":

    parser = argparse.ArgumentParser(description="Take thames summary table and turn into json by constituency")
    parser.add_argument("--output", type=pathlib.Path, default=pathlib.Path("datafiles/live/constituencies"),
                        help="directory for output files")
    args = parser.parse_args()

    os.makedirs(args.output, exist_ok=True)

    available_constituencies = []

    with psycopg2.connect(host="localhost", database="gis", user="docker", password="docker") as conn:
        for constituency, data in thames_summary(connection=conn):
            available_constituencies.append(constituency)
            with open(args.output / f"{utils.kebabcase(constituency)}.json", "w") as bob:
                json.dump(
                    fp=bob,
                    cls=MultipleJsonEncoders(DateTimeEncoder, TimeDeltaMinutesEncoder),
                    obj={
                        "cso": [
                            {"p": d["discharge_site_name"],
                             "cid": d["permit_id"],
                             "d": d["date"],
                             "a": Summariser().summarise(d["bucket"])
                             } for d in data
                        ],
                        "rainfall": [{
                            "d": date,
                            "c": rainfall.r_pct_75,
                            "r": f"r-{min(10, int(math.ceil(rainfall.r_pct_75 / 2)))}",
                            "n": rainfall.stations,
                        } for date, rainfall in rainfall_by_constituency(connection=conn, constituency=constituency).items()],
                    },
                )

        with open(args.output / "constituencies-available.json", "w") as bob:
            json.dump(fp=bob, obj=sorted(available_constituencies))

