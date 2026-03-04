package org.totp.model.data

enum class Companies(val companyName: CompanyName) {
    Anglian(CompanyNames.anglianWater),
    Northumbrian(CompanyNames.northumrianWater),
    SevernTrent(CompanyNames.severnTrentWater),
    SouthWest(CompanyNames.southWestWater),
    Southern(CompanyNames.southernWater),
    Thames(CompanyNames.thamesWater),
    United(CompanyNames.unitedUtilities),
    Yorkshire(CompanyNames.yorkshireWater),
    Wessex(CompanyNames.wessexWater),
    DwrCymru(CompanyNames.dwrCymru),
    Scottish(CompanyNames.scottishWater),
}

object CompanyNames {
    val anglianWater = CompanyName.of("Anglian Water")
    val northumrianWater = CompanyName.of("Northumbrian Water")
    val severnTrentWater = CompanyName.of("Severn Trent Water")
    val southWestWater = CompanyName.of("South West Water")
    val southernWater = CompanyName.of("Southern Water")
    val thamesWater = CompanyName.of("Thames Water")
    val unitedUtilities = CompanyName.of("United Utilities")
    val yorkshireWater = CompanyName.of("Yorkshire Water")
    val wessexWater = CompanyName.of("Wessex Water")
    val dwrCymru = CompanyName.of("Dwr Cymru Welsh Water")
    val scottishWater = CompanyName.of("Scottish Water")
}