import dataclasses
import enum
from typing import List

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
