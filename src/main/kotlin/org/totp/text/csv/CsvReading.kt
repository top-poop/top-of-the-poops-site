package org.totp.text.csv

import com.opencsv.CSVReader
import java.io.IOException
import java.io.StringReader

fun <T> readCSV(resource: String, mapper: (Array<String>) -> T): List<T> {
    val text = object {}
        .javaClass.getResource(resource)
        ?.readText()
        ?: throw IOException("Resource $resource not found")

    return CSVReader(StringReader(text)).use {
        it.readAll()
    }.map(mapper)
}