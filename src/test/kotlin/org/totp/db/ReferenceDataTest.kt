package org.totp.db

import org.junit.jupiter.api.Test
import org.totp.model.data.ConstituencyName
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import java.time.LocalDate

class ReferenceDataTest {


    val connection = HikariWithConnection(lazy { datasource() })

    val tw = ReferenceData(connection)

    @Test
    fun mps() {
        expectThat(tw.mps().size).isGreaterThan(600)
    }
}