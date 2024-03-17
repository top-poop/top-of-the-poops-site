package org.totp.db

import org.junit.jupiter.api.Test
import org.totp.model.data.ConstituencyName
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isGreaterThan
import strikt.assertions.size
import java.time.LocalDate

class EDMTest {


    val connection = HikariWithConnection(lazy { datasource() })

    val tw = EDM(connection)

    @Test
    fun annualSummary() {
        tw.annualSummariesForConstituency(ConstituencyName("Aldershot"))
    }
}