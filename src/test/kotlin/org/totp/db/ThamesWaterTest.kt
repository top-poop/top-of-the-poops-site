package org.totp.db

import org.junit.jupiter.api.Test
import org.totp.model.data.ConstituencyName
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import java.time.LocalDate

class ThamesWaterTest {


    val connection = HikariWithConnection(lazy { datasource() })

    val tw = ThamesWater(connection)

    @Test
    fun worst() {
        tw.worstCSOsInPeriod(
            startDate = LocalDate.parse("2024-01-01"),
            endDate = LocalDate.parse("2024-02-01")
        )
    }

    @Test
    fun haveLiveData() {
        expectThat(tw.haveLiveDataFor()).contains(ConstituencyName.of("Hertsmere"))
        expectThat(tw.haveLiveDataFor()).not().contains(ConstituencyName.of("Liverpool, Riverside"))
    }
}