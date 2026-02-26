import datetime

import events
import psy
from companies import WaterCompany
from streamdb import Database, StreamFile

pool = psy.connect("localhost")



def test_loading_events():

    a_file = StreamFile(WaterCompany.UnitedUtilities,
                        file_id="ca3932c5-0c06-42bc-95c2-e6bd21545a3d",
                        file_time=datetime.datetime.fromisoformat("2025-04-07 09:15:29.000000 +00:00"))

    with pool.connection() as conn:
        db = Database(conn)
        records = db.load_file_records(a_file)
        print(records)


def test_interpreting_single_cso():
    with pool.connection() as conn:
        db = Database(conn)

        company = WaterCompany.UnitedUtilities
        cso_id = 'UUG1130'
        file_events = db.load_file_events_for(company, cso_id)

        previous = None

        for feature_record in file_events:

            result = events.interpret(
                mapping={cso_id:cso_id},
                file=StreamFile(company=company, file_id="1", file_time=datetime.datetime.now()),
                previous=previous,
                f=feature_record
            )
            print(result)
            previous = result

