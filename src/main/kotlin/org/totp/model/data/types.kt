package org.totp.model.data

import dev.forkhandles.values.StringValue
import dev.forkhandles.values.StringValueFactory

class GeoJSON(value: String) : StringValue(value) {
    companion object : StringValueFactory<GeoJSON>(::GeoJSON)
}
