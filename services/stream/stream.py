import dataclasses
import datetime
import enum
import itertools
from typing import List, Dict, Optional

import requests
from requests.adapters import HTTPAdapter
from urllib3 import Retry


class WaterCompany(enum.Enum):
    Anglian = 1
    Northumbrian = 2
    SevernTrent = 3
    Southern = 4
    SouthWestWater = 5
    ThamesWater = 6
    UnitedUtilities = 7
    WessexWater = 8
    YorkshireWater = 9


@dataclasses.dataclass
class FeatureRecord:
    id: str
    status: str
    company: str
    statusStart: Optional[datetime.datetime]
    latestEventStart: Optional[datetime.datetime]
    latestEventEnd: Optional[datetime.datetime]
    lastUpdated: datetime.datetime
    lat: float
    lon: float
    receivingWater: str


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


def timestamp(epochMs: Optional[int]) -> Optional[datetime.datetime]:
    if epochMs is None:
        return None

    return datetime.datetime.fromtimestamp(epochMs / 1000.0, tz=datetime.UTC)


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

        return FeatureList(
            name=resp['objectIdFieldName'],
            ids=resp['objectIds']
        )

    def _features(self, oid: str, ids: List[int]) -> List[FeatureRecord]:
        things = ','.join([str(id) for id in ids])
        response = self.session.get(self.base_uri, params={
            'where': f"1=1",
            'outFields': '*',
            'outSR': 4326,
            'f': 'json',
            'objectIds': things,
        })
        response.raise_for_status()

        resp = response.json()

        def to_record(f: Dict) -> FeatureRecord:
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

    def features(self):
        feature_list = self._feature_list()

        groups = itertools.batched(feature_list.ids, 100)

        return itertools.chain.from_iterable(
            [self._features(feature_list.name, g) for g in groups]
        )


import logging

if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG)

    api = StreamAPI(company=WaterCompany.WessexWater)

    features = api.features()
    for f in features:
        print(f)
