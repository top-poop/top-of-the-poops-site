import enum


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
    DwrCymru = 10


StreamMembers = [w for w in WaterCompany if w not in {WaterCompany.DwrCymru}]
