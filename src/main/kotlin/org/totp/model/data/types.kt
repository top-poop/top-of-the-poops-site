package org.totp.model.data

import com.google.common.collect.HashBiMap
import dev.forkhandles.values.ComparableValue
import dev.forkhandles.values.StringValue
import dev.forkhandles.values.StringValueFactory
import org.totp.extensions.kebabCase

data class Coordinates(val lat: Number, val lon: Number)


class GeoJSON(value: String) : StringValue(value) {
    companion object : StringValueFactory<GeoJSON>(::GeoJSON)
}

class ConstituencyName(value: String) : StringValue(value), ComparableValue<ConstituencyName, String> {
    companion object : StringValueFactory<ConstituencyName>(::ConstituencyName)
}

class ConstituencySlug(value: String) : StringValue(value) {
    companion object : StringValueFactory<ConstituencySlug>(::ConstituencySlug)
}

fun ConstituencyName.toSlug(): ConstituencySlug = ConstituencySlug.of(value.kebabCase())

class WaterwayName(value: String) : StringValue(value), ComparableValue<WaterwayName, String> {
    companion object : StringValueFactory<WaterwayName>(::WaterwayName)
}

private val streamEABiMap = HashBiMap.create(
    mapOf(
        StreamCompanyName.of("Anglian") to CompanyName.of("Anglian Water"),
        StreamCompanyName.of("Northumbrian") to CompanyName.of("Northumbrian Water"),
        StreamCompanyName.of("SevernTrent") to CompanyName.of("Severn Trent Water"),
        StreamCompanyName.of("SouthWestWater") to CompanyName.of("South West Water"),
        StreamCompanyName.of("Southern") to CompanyName.of("Southern Water"),
        StreamCompanyName.of("ThamesWater") to CompanyName.of("Thames Water"),
        StreamCompanyName.of("UnitedUtilities") to CompanyName.of("United Utilities"),
        StreamCompanyName.of("WessexWater") to CompanyName.of("Wessex Water"),
        StreamCompanyName.of("DwrCymru") to CompanyName.of("Dwr Cymru Welsh Water"),
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

fun ShellfisheryName.toSlug() = ShellfishSlug.of(value.kebabCase())

class ShellfishAreaName(value: String) : StringValue(value), ComparableValue<ShellfishAreaName, String> {
    companion object : StringValueFactory<ShellfishAreaName>(::ShellfishAreaName)
}

class ShellfishSlug(value: String) : StringValue(value) {
    companion object : StringValueFactory<ShellfishSlug>(::ShellfishSlug)
}

fun BathingName.toSlug() = BathingSlug.of(value.kebabCase())

class BathingSlug(value: String) : StringValue(value) {
    companion object : StringValueFactory<BathingSlug>(::BathingSlug)
}

/** name of a beach, as designated "sensitive area bathing" */
class BeachName(value: String) : StringValue(value), ComparableValue<BeachName, String> {
    companion object : StringValueFactory<BeachName>(::BeachName)
}

class CompanySlug(value: String) : StringValue(value) {
    companion object : StringValueFactory<CompanySlug>(::CompanySlug)
}

fun CompanyName.toSlug(): CompanySlug = CompanySlug.of(value.kebabCase())

