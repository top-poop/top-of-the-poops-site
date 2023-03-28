package org.totp.extensions

import com.fasterxml.jackson.databind.ObjectMapper

fun ObjectMapper.readSimpleList(s: String): List<Map<String, Any?>> {
    return readerForListOf(HashMap::class.java)
        .readValue(s)
}

fun ObjectMapper.readSimpleMap(s: String): Map<String, Any> {
    return readerForMapOf(HashMap::class.java).readValue(s)
}

