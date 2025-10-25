from storage import StreamCSV
from stream import FeatureRecord, DwrCymruRecord
from storage import DwrCymruCSV

ang = """lastUpdated,id,status,statusStart,latestEventStart,latestEventEnd,company,lat,lon,receivingWater
2024-12-31T23:43:35.676000+00:00,AnW0001,0,2024-12-04T10:30:15+00:00,2024-12-04T10:22:38+00:00,2024-12-04T10:30:15+00:00,Anglian Water Services,52.112837,-1.0640481,THE RIVER TOVE
,AnW0002,0,,,,Anglian Water Services,53.283803,0.11982489,Un-Named Dyke to Great Eau
2024-12-31T23:43:35.676000+00:00,AnW0003,0,2024-12-30T11:35:57+00:00,2024-12-30T11:30:34+00:00,2024-12-30T11:35:57+00:00,Anglian Water Services,52.035951,-0.96550124,Leckhampstead Brook
"""


def test_storage_stream():
    c = StreamCSV()
    r = c.from_csv(ang)

    assert len(r) == 3
    assert type(r[0]) == FeatureRecord
    assert r[0].status == '0'
    assert r[0].receivingWater == "THE RIVER TOVE"


def test_storage_dc():
    f = """assetName,asset_location,status,GlobalID,EditDate,discharge_duration_last_7_daysH,stop_date_time_discharge,start_date_time_discharge,discharge_duration_hours,discharge_x_location,discharge_y_location,Overflow,Linked_Bathing_Water,Receiving_Water,lat,lon
Ffos-y-ffin Storm Overflow,SN4483160856,Overflow Not Operating,661ea451-da46-4f9a-87d1-632bbe91071d,2025-01-31T05:46:21.407000+00:00,,2025-01-28T11:30:00,2025-01-28T11:00:24,0.5,244766,260870,Storm,,Ceri Brook,52.2241106804165,-4.27410360655607
"""

    c = DwrCymruCSV()
    r = c.from_csv(f)

    assert type(r[0]) == DwrCymruRecord
    assert r[0].status == 'Overflow Not Operating'
    assert r[0].discharge_duration_last_7_daysH is None
