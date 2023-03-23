package org.totp.extensions

import com.fasterxml.jackson.databind.ObjectMapper

fun ObjectMapper.readSimpleList(s: String): List<Map<String, Any?>> {
    return readerForListOf(HashMap::class.java)
        .readValue(s)
}