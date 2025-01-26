package org.totp.db

import org.junit.jupiter.api.Test
import org.totp.model.data.ConstituencyName
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isGreaterThan
import strikt.assertions.size
import java.time.Instant
import java.time.LocalDate

class EnvrionmentAgencyTest {


    val connection = HikariWithConnection(lazy { datasource() })
    val tw = EnvironmentAgency(connection)


    @Test
    fun eventSummaryForConstituency() {
        val x = tw.rainfallForConstituency(ConstituencyName.of("Aldershot"), LocalDate.parse("2023-01-01"), LocalDate.parse("2024-01-01"))
        expectThat(x).size.isGreaterThan(50)
    }

    @Test
    fun rainfallAt() {
        val x = tw.rainfallGridAt(Instant.parse("2024-01-01T00:00:00Z"))
        expectThat(x).size.isGreaterThan(100)
    }
}