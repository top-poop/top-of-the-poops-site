package org.totp.model.data

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
    companion object : StringValueFactory<ConstituencySlug>(::ConstituencySlug) {
        fun from(name: ConstituencyName): ConstituencySlug {
            return of(name.value.kebabCase())
        }
    }
}

class WaterwayName(value: String) : StringValue(value), ComparableValue<WaterwayName, String> {
    companion object : StringValueFactory<WaterwayName>(::WaterwayName)
}

class CompanyName(value: String) : StringValue(value), ComparableValue<CompanyName, String> {
    companion object : StringValueFactory<CompanyName>(::CompanyName)
}

/** name of a bathing location as given in EDM file */
class BathingName(value: String) : StringValue(value), ComparableValue<BathingName, String> {
    companion object : StringValueFactory<BathingName>(::BathingName)
}

fun BathingName.toSlug() = BathingSlug.of(value.kebabCase())

class BathingSlug(value: String) : StringValue(value) {
    companion object : StringValueFactory<BathingSlug>(::BathingSlug)
}

/** name of a beach, as designated "sensitive area bathing" */
class BeachName(value: String) : StringValue(value), ComparableValue<BeachName, String> {
    companion object : StringValueFactory<BeachName>(::BeachName)
}



