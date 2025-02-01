import dataclasses
import datetime
import itertools
from enum import Enum
from typing import List, Dict, Optional

import requests
from requests.adapters import HTTPAdapter
from requests.structures import CaseInsensitiveDict
from urllib3 import Retry

from companies import WaterCompany


class EventType(Enum):
    Start = 1
    Stop = 0
    Offline = -1


@dataclasses.dataclass(frozen=True)
class FeatureRecord:
    id: str
    status: str
    company: str
    statusStart: Optional[datetime.datetime]
    latestEventStart: Optional[datetime.datetime]
    latestEventEnd: Optional[datetime.datetime]
    lastUpdated: Optional[datetime.datetime]
    lat: float
    lon: float
    receivingWater: str


@dataclasses.dataclass(frozen=True)
class DwrCymruRecord:
    asset_name : str
    asset_location : str
    status : str
    GlobalID : str
    EditDate : int
    discharge_duration_last_7_daysH : str
    stop_date_time_discharge : datetime.datetime
    start_date_time_discharge : datetime.datetime
    discharge_duration_hours : float
    discharge_x_location : int
    discharge_y_location : int
    Overflow : str
    Linked_Bathing_Water : Optional[str]
    Receiving_Water : str
    lat: float
    lon: float


x = {
    "OBJECTID": 1539,
    "Id": "NES0141",
    "Company": "Northumbrian Water Ltd",
    "Status": 0,
    "StatusStart": 1735342680006,
    "LatestEventStart": 1735342667010,
    "LatestEventEnd": 1735342680006,
    "Latitude": 54.60886,
    "Longitude": -1.13235,
    "ReceivingWaterCourse": "Tees Estuary (S Bank)",
    "LastUpdated": 1735345436350
}

# Status
# Anglian -> int
# Northumbrian -> coded -> -1 offline, 0 stop, 1 start
# Severn Trent -> coded -> -1 offline, 0 stop, 1 start
# Southern -> coded -> -1 offline, 0 stop, 1 start
# South West Water -> coded -> -1 offline, 0 stop, 1 start
#

data_urls = {
    WaterCompany.Anglian: "https://services3.arcgis.com/VCOY1atHWVcDlvlJ/arcgis/rest/services/stream_service_outfall_locations_view/FeatureServer/0/query",
    WaterCompany.Northumbrian: "https://services-eu1.arcgis.com/MSNNjkZ51iVh8yBj/arcgis/rest/services/Northumbrian_Water_Storm_Overflow_Activity_2_view/FeatureServer/0/query",
    WaterCompany.SevernTrent: "https://services1.arcgis.com/NO7lTIlnxRMMG9Gw/arcgis/rest/services/Severn_Trent_Water_Storm_Overflow_Activity/FeatureServer/0/query",
    WaterCompany.Southern: "https://services-eu1.arcgis.com/XxS6FebPX29TRGDJ/arcgis/rest/services/Southern_Water_Storm_Overflow_Activity/FeatureServer/0/query",
    WaterCompany.SouthWestWater: "https://services-eu1.arcgis.com/OMdMOtfhATJPcHe3/arcgis/rest/services/NEH_outlets_PROD/FeatureServer/0/query",
    WaterCompany.ThamesWater: "https://services2.arcgis.com/g6o32ZDQ33GpCIu3/arcgis/rest/services/Thames_Water_Storm_Overflow_Activity_(Production)_view/FeatureServer/0/query",
    WaterCompany.UnitedUtilities: "https://services5.arcgis.com/5eoLvR0f8HKb7HWP/arcgis/rest/services/United_Utilities_Storm_Overflow_Activity/FeatureServer/0/query",
    WaterCompany.WessexWater: "https://services.arcgis.com/3SZ6e0uCvPROr4mS/arcgis/rest/services/Wessex_Water_Storm_Overflow_Activity/FeatureServer/0/query",
    WaterCompany.YorkshireWater: "https://services-eu1.arcgis.com/1WqkK5cDKUbF0CkH/arcgis/rest/services/Yorkshire_Water_Storm_Overflow_Activity/FeatureServer/0/query",
}


@dataclasses.dataclass
class FeatureList:
    name: str
    ids: List[int]


def timestamp(epoch_ms: Optional[int]) -> Optional[datetime.datetime]:
    if epoch_ms is None:
        return None

    return datetime.datetime.fromtimestamp(epoch_ms / 1000.0, tz=datetime.UTC)


class StreamAPI:

    def __init__(self, company: WaterCompany):
        self.session = requests.Session()
        self.session.mount('https://', HTTPAdapter(
            max_retries=(Retry(total=3, backoff_factor=0.5, status_forcelist=[500, 502, 503, 504]))
        ))
        self.company = company
        self.base_uri = data_urls[company]

    def _feature_list(self) -> FeatureList:
        response = self.session.get(self.base_uri, params={
            'where': '1=1',
            'outFields': '*',
            'outSR': 4326,
            'f': 'json',
            'returnIdsOnly': 'true'
        })
        response.raise_for_status()
        resp = response.json()

        ids = resp['objectIds']

        print(f">>> Have {len(ids)} object ids to retrieve")
        ids = list(set(ids))
        print(f">>> Have {len(ids)} unique object ids to retrieve")

        return FeatureList(
            name=resp['objectIdFieldName'],
            ids=ids
        )

    def _features(self, oids: List[int]) -> List[FeatureRecord]:
        things = ','.join([str(oid) for oid in oids])
        response = self.session.get(self.base_uri, params={
            'where': f"1=1",
            'outFields': '*',
            'outSR': 4326,
            'f': 'json',
            'objectIds': things,
        })
        response.raise_for_status()

        resp = response.json()

        def to_record(d: Dict) -> FeatureRecord:
            f = CaseInsensitiveDict(data=d)
            return FeatureRecord(
                id=f["Id"],
                status=f["Status"],
                statusStart=timestamp(f["StatusStart"]),
                company=f["Company"],
                lastUpdated=timestamp(f["LastUpdated"]),
                latestEventStart=timestamp(f["LatestEventStart"]),
                latestEventEnd=timestamp(f["LatestEventEnd"]),
                lat=f["Latitude"],
                lon=f["Longitude"],
                receivingWater=f["ReceivingWaterCourse"],
            )

        return [to_record(f["attributes"]) for f in resp["features"]]

    def features(self) -> List[FeatureRecord]:
        feature_list = self._feature_list()

        groups = itertools.batched(feature_list.ids, 100)

        return list(itertools.chain.from_iterable(
            [self._features(g) for g in groups]
        ))


import logging

if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG)

    api = StreamAPI(company=WaterCompany.SevernTrent)

    features = api.features()
    # for f in features:

    #     print(f)

    print()