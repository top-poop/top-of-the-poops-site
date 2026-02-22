package org.totp.db

import org.junit.jupiter.api.Test
import org.totp.model.data.ConstituencyName
import org.totp.model.data.Coordinates
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import strikt.assertions.isNull

class ReferenceDataTest {


    val connection = HikariWithConnection(lazy { datasource() })

    val tw = ReferenceData(connection)

    @Test
    fun mps() {
        expectThat(tw.mps().size).isGreaterThan(600)
    }

    @Test
    fun `constituency by location`() {
        expectThat(tw.constituencyAt(Coordinates(50.826, -2.308)))
            .isEqualTo(ConstituencyName("West Dorset"))
    }

    @Test
    fun `constituency by location - missing`() {
        expectThat(tw.constituencyAt(Coordinates(1.0, 1.0)))
            .isNull()
    }

    @Test
    fun `constituency near - nearest`() {
        expectThat(tw.constituencyNear(Coordinates(1.0, 1.0)))
            .isEqualTo(ConstituencyName("St Ives"))
    }
}