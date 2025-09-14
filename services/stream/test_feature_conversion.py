import datetime

from stream import DwrCymruRecord, EventType

another_date = datetime.datetime.fromisoformat("2034-04-03T23:45:56")
some_date = datetime.datetime.fromisoformat("2001-04-03T23:45:56")
later_date = datetime.datetime.fromisoformat("2001-05-03T23:45:56")


def test_convert_dwr_start():
    d = DwrCymruRecord(assetName="name", asset_location="location", GlobalID="xx-yy-zz", EditDate=another_date,
                       status="Overflow Operating", discharge_duration_last_7_daysH=0.0,
                       stop_date_time_discharge=None, start_date_time_discharge=some_date,
                       discharge_duration_hours=1.1, discharge_x_location=0, discharge_y_location=0,
                       Overflow="Emergency or Storm", Linked_Bathing_Water="bathing", Receiving_Water="receiving",
                       lat=123, lon=456)
    f = d.as_feature_record()

    assert f.status == EventType.Start
    assert f.statusStart == some_date
    assert f.latestEventEnd is None
    assert f.lat == 123
    assert f.lon == 456
    assert f.id == d.GlobalID


def test_convert_dwr_stop():
    d = DwrCymruRecord(assetName="name", asset_location="location", GlobalID="xx-yy-zz", EditDate=another_date,
                       status="Overflow Not Operating", discharge_duration_last_7_daysH=0.0,
                       stop_date_time_discharge=later_date,
                       start_date_time_discharge=some_date,
                       discharge_duration_hours=1.1, discharge_x_location=0, discharge_y_location=0,
                       Overflow="Emergency or Storm", Linked_Bathing_Water="bathing", Receiving_Water="receiving",
                       lat=123, lon=456)
    f = d.as_feature_record()

    assert f.status == EventType.Stop
    assert f.statusStart == later_date
    assert f.latestEventStart == later_date
    assert f.latestEventEnd == later_date
    assert f.lat == 123
    assert f.lon == 456
    assert f.id == d.GlobalID
