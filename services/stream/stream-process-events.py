import argparse
import logging
import os
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import List, Callable

from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool

import psy
from args import enum_parser
from companies import WaterCompany
from events import interpret
from stream import FeatureRecord
from streamdb import Database, StreamEvent



logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s.%(msecs)03dZ %(levelname)s [%(name)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)

logger = logging.getLogger(__name__)


class EventProcessor:

    def __init__(self, pool: ConnectionPool, feature_filter: Callable[[FeatureRecord], bool]):
        self.pool = pool
        self.feature_filter = feature_filter

    def process_events(self, company: WaterCompany) -> bool:

        with self.pool.connection() as conn:

            database = Database(conn)

            ids = database.load_ids(company)

            unprocessed_files = database.files_unprocessed(company)

            logger.info(f"{company}: Files to process: {len(unprocessed_files)}")

            latest_by_id = database.latest_cso_events(company)

            new_csos_found = False

            for file in unprocessed_files:

                logger.info(f"Processing Events from {file}")
                s = time.time()

                new_events: List[StreamEvent] = []

                features = database.load_file_records(file)

                # logger.info(f"{company}: Loaded {len(features)} potential events")

                new_cso_features = [f for f in features if f.id not in ids]
                if new_cso_features:
                    logger.info(f"{company}: Found {len(new_cso_features)} new CSOs")
                    new_csos_found = True
                    database.insert_cso(company=company, features=new_cso_features)
                    ids = database.load_ids(company=company)

                for f in [g for g in features if feature_filter(g)]:

                    if f.id == '':
                        logger.info(f">> {company}: Record has no id? file = {file}")

                    try:
                        new_event = interpret(ids, file=file, previous=latest_by_id.get(f.id), f=f)
                        if new_event is not None:
                            latest_by_id[f.id] = new_event
                            new_events.append(new_event)

                    except Exception as e:
                        logger.info(f"{company}: {f.id} error")
                        raise

                if new_events:
                    database.insert_cso_events(events=new_events)
                    # logger.info(f"{company}: Inserted {len(new_events)} events in {time.time() - s}")

                database.mark_processed(file)
                conn.commit()

        return new_csos_found


if __name__ == '__main__':

    parser = argparse.ArgumentParser(description="Attempt to parse events from stream status files")
    parser.add_argument("--company", type=enum_parser(WaterCompany), nargs="+", help="company (default: all)")
    parser.add_argument("--id", help="id (default: all)")

    args = parser.parse_args()

    db_host = os.environ.get("DB_HOST", "localhost")

    if args.company:
        companies = args.company
    else:
        companies = [w for w in WaterCompany if w != WaterCompany.YorkshireWater]

    if args.id:
        feature_filter = lambda x: x.id == args.id
    else:
        feature_filter = lambda x: True

    update_materialised_views = False

    pool = psy.connect(db_host)

    event_processor = EventProcessor(pool, feature_filter)

    with ThreadPoolExecutor(max_workers=5) as executor:
        futures = [executor.submit(event_processor.process_events, company) for company in companies]

        results = []
        for future in as_completed(futures):
            results.append(future.result())

        update_materialised_views = any(results)

    if update_materialised_views:
        logger.info("Updating views...")
        with pool.connection() as conn:
            with conn.cursor() as cursor:
                cursor.execute("refresh materialized view stream_cso_grid")