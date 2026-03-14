package org.totp.model.data

import com.google.common.collect.HashBiMap
import dev.forkhandles.values.ComparableValue
import dev.forkhandles.values.StringValue
import dev.forkhandles.values.StringValueFactory
import org.totp.extensions.kebabCase

data class Coordinates(val lat: Double, val lon: Double)

data class BoundingBox(val ne: Coordinates, val sw: Coordinates)

class GeoJSON(value: String) : StringValue(value) {
    companion object : StringValueFactory<GeoJSON>(::GeoJSON)
}

class ConstituencyName(value: String) : StringValue(value), ComparableValue<ConstituencyName, String> {
    companion object : StringValueFactory<ConstituencyName>(::ConstituencyName)
}

class SeneddConstituencyName(value: String) : StringValue(value), ComparableValue<SeneddConstituencyName, String> {
    companion object : StringValueFactory<SeneddConstituencyName>(::SeneddConstituencyName)
}

class SiteName(value: String) : StringValue(value), ComparableValue<SiteName, String> {
    companion object : StringValueFactory<SiteName>(::SiteName)
}

class PlaceName(value: String) : StringValue(value), ComparableValue<PlaceName, String> {
    companion object : StringValueFactory<PlaceName>(::PlaceName)
}

class Slug(value: String) : StringValue(value),  ComparableValue<Slug, String> {
    companion object : StringValueFactory<Slug>(::Slug)
}

fun StringValue.toSlug(): Slug = Slug.of(value.kebabCase())

class WaterwayName(value: String) : StringValue(value), ComparableValue<WaterwayName, String> {
    companion object : StringValueFactory<WaterwayName>(::WaterwayName)
}

private val streamEABiMap = HashBiMap.create(
    mapOf(
        StreamCompanyName.of("Anglian") to CompanyNames.anglianWater,
        StreamCompanyName.of("Northumbrian") to CompanyNames.northumrianWater,
        StreamCompanyName.of("SevernTrent") to CompanyNames.severnTrentWater,
        StreamCompanyName.of("SouthWestWater") to CompanyNames.southWestWater,
        StreamCompanyName.of("Southern") to CompanyNames.southernWater,
        StreamCompanyName.of("ThamesWater") to CompanyNames.thamesWater,
        StreamCompanyName.of("UnitedUtilities") to CompanyNames.unitedUtilities,
        StreamCompanyName.of("YorkshireWater") to CompanyNames.yorkshireWater,
        StreamCompanyName.of("WessexWater") to CompanyNames.wessexWater,
        StreamCompanyName.of("DwrCymru") to CompanyNames.dwrCymru,
    )
)

class CompanyName(value: String) : StringValue(value), ComparableValue<CompanyName, String> {
    companion object : StringValueFactory<CompanyName>(::CompanyName)

    fun asStreamCompanyName(): StreamCompanyName? {
        return streamEABiMap.inverse()[this]
    }
}

class StreamCompanyName(value: String) : StringValue(value), ComparableValue<StreamCompanyName, String> {
    companion object : StringValueFactory<StreamCompanyName>(::StreamCompanyName)

    fun asCompanyName(): CompanyName? {
        return streamEABiMap[this]
    }
}

/** name of a bathing location as given in EDM file */
class BathingName(value: String) : StringValue(value), ComparableValue<BathingName, String> {
    companion object : StringValueFactory<BathingName>(::BathingName)
}

/** name of a shellfish location as given in EDM file */
class ShellfisheryName(value: String) : StringValue(value), ComparableValue<ShellfisheryName, String> {
    companion object : StringValueFactory<ShellfisheryName>(::ShellfisheryName)
}

class ShellfishAreaName(value: String) : StringValue(value), ComparableValue<ShellfishAreaName, String> {
    companion object : StringValueFactory<ShellfishAreaName>(::ShellfishAreaName)
}

/** name of a beach, as designated "sensitive area bathing" */
class BeachName(value: String) : StringValue(value), ComparableValue<BeachName, String> {
    companion object : StringValueFactory<BeachName>(::BeachName)
}
