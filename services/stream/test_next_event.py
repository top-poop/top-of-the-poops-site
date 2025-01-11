import datetime
from typing import Optional, List

from companies import WaterCompany
from events import bob
from stream import FeatureRecord, EventType
from streamdb import StreamEvent
from streamdb import StreamFile

### The water companies have differing interpretations of what the various different states mean.

## anglian on a stop event uses 'statusstart' to be the time that the stop event was received
## northhumbrian seems to do the same, also severn trent
## neither has reliable information in the 'latest event start  / end' field
events_anglian = {
    "initial_stop": [
        "Stop,2024-12-13 02:44:46.000000 +00:00,2024-12-11 02:43:23.000000 +00:00,2024-12-13 02:44:46.000000 +00:00,2024-12-31 12:16:40.810000 +00:00,2024-12-31 12:16:40.810000 +00:00"],
    "initial_stop_nulls": ["Stop,,,"],
    "stop_start_stop": [
        "Stop,2024-12-30 11:35:57.000000 +00:00,2024-12-30 11:30:34.000000 +00:00,2024-12-30 11:35:57.000000 +00:00,2024-12-31 12:16:28.827000 +00:00",
        "Start,2025-01-01 11:45:38.000000 +00:00,2025-01-01 11:45:38.000000 +00:00,,2025-01-01 12:44:04.028000 +00:00",
        "Stop,2025-01-03 01:15:24.000000 +00:00,2025-01-01 11:45:38.000000 +00:00,2025-01-03 01:15:24.000000 +00:00,2025-01-03 01:43:27.598000 +00:00",
    ],
    "stop_stop_stop": [
        "Stop,2024-12-31 14:30:27.000000 +00:00,2024-12-31 14:30:27.000000 +00:00,2024-12-31 14:30:27.000000 +00:00,2024-12-31 15:13:44.951000 +00:00",
        "Stop,2024-12-31 18:45:57.000000 +00:00,2024-12-31 18:45:57.000000 +00:00,2024-12-31 18:45:57.000000 +00:00,2024-12-31 19:13:51.040000 +00:00",
        "Stop,2024-12-31 20:04:16.000000 +00:00,2024-12-31 20:01:41.000000 +00:00,2024-12-31 20:04:16.000000 +00:00,2024-12-31 20:44:19.489000 +00:00",
    ],
    "start_start_start_stop": [
        "Start,2025-01-02 00:16:05.000000 +00:00,2025-01-02 00:16:05.000000 +00:00,,2025-01-02 00:44:13.148000 +00:00",
        "Start,2025-01-02 00:46:08.000000 +00:00,2025-01-02 00:46:08.000000 +00:00,,2025-01-02 01:14:34.735000 +00:00",
        "Start,2025-01-02 01:16:16.000000 +00:00,2025-01-02 01:16:16.000000 +00:00,,2025-01-02 01:44:18.519000 +00:00",
        "Stop,2025-01-02 01:31:23.000000 +00:00,2025-01-02 01:16:16.000000 +00:00,2025-01-02 01:31:23.000000 +00:00,2025-01-02 02:14:30.316000 +00:00",
    ],
    "offline": [
        "Offline,2024-03-01 00:00:00.000000 +00:00,,,2024-10-30 14:16:13.573000 +00:00"
    ]
}

events_southern = {
    "initial_stop": [
        "Stop,2021-10-02 18:25:00.000000 +00:00,2021-10-02 18:25:00.000000 +00:00,2021-10-02 18:40:00.000000 +00:00,2024-12-31 12:15:01.120000 +00:00"
    ],
    "stop_start_stop": [
        "Stop,2024-11-27 07:00:00.000000 +00:00,2024-11-27 07:00:00.000000 +00:00,2024-11-27 07:45:00.000000 +00:00,2024-12-31 12:15:01.120000 +00:00",
        "Start,2025-01-01 13:40:53.000000 +00:00,2025-01-01 13:40:53.000000 +00:00,,2025-01-01 15:15:00.717000 +00:00",
        "Stop,2025-01-01 13:40:53.000000 +00:00,2025-01-01 13:40:53.000000 +00:00,2025-01-01 14:45:00.000000 +00:00,2025-01-01 15:45:00.973000 +00:00",
    ],
    "start_start_stop": [
        "Start,2025-01-01 16:50:08.000000 +00:00,2025-01-01 16:50:08.000000 +00:00,,2025-01-01 18:00:00.123000 +00:00",
        "Start,2025-01-02 00:01:05.000000 +00:00,2025-01-02 00:01:05.000000 +00:00,,2025-01-02 01:00:00.847000 +00:00",
        "Stop,2025-01-02 00:01:05.000000 +00:00,2025-01-02 00:01:05.000000 +00:00,2025-01-02 00:20:49.000000 +00:00,2025-01-02 01:30:00.910000 +00:00",
    ]
}


def parse_date(s: str) -> Optional[datetime.datetime]:
    if s is None or s.strip() == '':
        return None
    return datetime.datetime.fromisoformat(s)


def as_feature(company: WaterCompany, s: str):
    ss = s.split(",")

    return FeatureRecord(
        id="feature-id",
        status=EventType[ss[0]],
        company=company.name,
        statusStart=parse_date(ss[1]),
        latestEventStart=parse_date(ss[2]),
        latestEventEnd=parse_date(ss[3]),
        lat=0,
        lon=0,
        receivingWater='',
        lastUpdated=datetime.datetime.now()
    )


id_mapping = {"feature-id": "cso-id"}


def apply_events(file: StreamFile, fs: List[FeatureRecord]) -> List[StreamEvent]:
    previous = None
    out = []
    for f in fs:
        result = bob(id_mapping, file=file, previous=previous, f=f)
        if result is not None:
            previous = result
            out.append(result)
    return out


class TestAnglian:
    company = WaterCompany.Anglian
    file = StreamFile(company=WaterCompany.Anglian, file_id='1234', file_time=datetime.datetime.now())

    def events(self, scenario: str) -> List[FeatureRecord]:
        return [as_feature(company=self.company, s=s) for s in events_anglian[scenario]]

    def test_initial_stop(self):
        """first run: we got a stop event, populated, so take that as initial event"""
        events = self.events('initial_stop')
        output = apply_events(self.file, events)
        assert len(output) == 1
        assert output[0] == StreamEvent(cso_id='cso-id',
                                        event=EventType.Stop,
                                        event_time=events[0].statusStart,
                                        file_id=TestAnglian.file.file_id,
                                        update_time=events[0].lastUpdated)

    def test_initial_stop_nulls(self):
        """first run: we got a stop event, but all data was null, so synthesize the dates"""
        events = self.events('initial_stop_nulls')
        output = apply_events(self.file, events)
        assert len(output) == 1
        assert output[0] == StreamEvent(cso_id='cso-id',
                                        event=EventType.Stop,
                                        event_time=self.file.file_time,
                                        file_id=TestAnglian.file.file_id,
                                        update_time=self.file.file_time)

    def test_stop_start_stop(self):
        """stopped: get start with statusStart, then stop with statusStart"""
        events = self.events('stop_start_stop')
        output = apply_events(self.file, events)
        assert len(output) == 3
        # first event ignore, state is now 'Stop'
        assert output[1] == StreamEvent(cso_id='cso-id',
                                        event=EventType.Start,
                                        event_time=events[1].statusStart,
                                        file_id=TestAnglian.file.file_id,
                                        update_time=events[1].lastUpdated)
        assert output[2] == StreamEvent(cso_id='cso-id',
                                        event=EventType.Stop,
                                        event_time=events[2].statusStart,
                                        file_id=TestAnglian.file.file_id,
                                        update_time=events[2].lastUpdated)

    def test_stop_stop_stop(self):
        """stopped: initial will be stop, then ignore subsequent"""
        events = self.events('stop_stop_stop')
        output = apply_events(self.file, events)
        assert len(output) == 1

    def test_start_start_start_stop(self):
        """stopped: initial will be start, then ignore subsequent starts until stop"""
        events = self.events('start_start_start_stop')
        output = apply_events(self.file, events)
        assert len(output) == 2
        assert output[0] == StreamEvent(cso_id='cso-id',
                                        event=EventType.Start,
                                        event_time=events[0].statusStart,
                                        file_id=TestAnglian.file.file_id,
                                        update_time=events[0].lastUpdated)
        assert output[1] == StreamEvent(cso_id='cso-id',
                                        event=EventType.Stop,
                                        event_time=events[3].statusStart,
                                        file_id=TestAnglian.file.file_id,
                                        update_time=events[3].lastUpdated)

    def test_offline(self):
        """offline: ignored for now"""
        events = self.events('offline')
        output = apply_events(self.file, events)
        assert len(output) == 0


class TestSouthern:
    company = WaterCompany.Southern
    file = StreamFile(company=WaterCompany.Southern, file_id='4567', file_time=datetime.datetime.now())

    def events(self, scenario: str) -> List[FeatureRecord]:
        return [as_feature(company=self.company, s=s) for s in events_southern[scenario]]

    def test_initial_stop(self):
        """first run: we got a stop event, populated, so take that as initial event"""
        events = self.events('initial_stop')
        output = apply_events(self.file, events)
        assert len(output) == 1
        assert output[0] == StreamEvent(cso_id='cso-id',
                                        event=EventType.Stop,
                                        event_time=events[0].statusStart,
                                        file_id=TestSouthern.file.file_id,
                                        update_time=events[0].lastUpdated)

    def test_stop_start_stop(self):
        """stopped: get start with statusStart, then stop with latesteventend"""
        events = self.events('stop_start_stop')
        output = apply_events(self.file, events)
        assert len(output) == 3
        # first event ignore, state is now 'Stop'
        assert output[1] == StreamEvent(cso_id='cso-id',
                                        event=EventType.Start,
                                        event_time=events[1].statusStart,
                                        file_id=TestSouthern.file.file_id,
                                        update_time=events[1].lastUpdated)
        assert output[2] == StreamEvent(cso_id='cso-id',
                                        event=EventType.Stop,
                                        event_time=events[2].latestEventEnd,
                                        file_id=TestSouthern.file.file_id,
                                        update_time=events[2].lastUpdated)

    def test_start_start_stop(self):
        """stopped: get start with statusStart, then stop with latesteventend"""
        events = self.events('start_start_stop')
        output = apply_events(self.file, events)
        assert len(output) == 3
        assert output[0] == StreamEvent(cso_id='cso-id',
                                        event=EventType.Start,
                                        event_time=events[0].statusStart,
                                        file_id=TestSouthern.file.file_id,
                                        update_time=events[0].lastUpdated)

        assert output[1] == StreamEvent(cso_id='cso-id',
                                        event=EventType.Start,
                                        event_time=events[1].statusStart,
                                        file_id=TestSouthern.file.file_id,
                                        update_time=events[1].lastUpdated)
        assert output[2] == StreamEvent(cso_id='cso-id',
                                        event=EventType.Stop,
                                        event_time=events[2].latestEventEnd,
                                        file_id=TestSouthern.file.file_id,
                                        update_time=events[2].lastUpdated)
