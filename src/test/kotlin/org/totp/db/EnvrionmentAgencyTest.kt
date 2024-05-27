package org.totp.db

import org.junit.jupiter.api.Test
import org.totp.model.data.ConstituencyName
import strikt.api.expectThat
import strikt.assertions.*
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
    fun geometry() {
        val waterbodyId = EnvironmentAgency.WaterbodyId.of("GB102021072960")
        expectThat(tw.waterwayGeometry(waterbodyId)).isNotNull().and {
            get { this.id}.isEqualTo(waterbodyId)
            get { this.name}.isEqualTo(EnvironmentAgency.WaterbodyName.of("Glen from Source to College Burn"))
            get { this.geometry.value}.startsWith("{")
        }
    }

}