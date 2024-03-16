package org.totp.db

import org.junit.jupiter.api.Test
import org.totp.model.data.ConstituencyName
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isGreaterThan
import strikt.assertions.size
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
    fun infrastructure() {
        val x = tw.infrastructureSummary()

        expectThat(x).size.isGreaterThan(100)
    }

    @Test
    fun eventSummaryForCSO() {
        val x = tw.eventSummaryForCSO("TEMP.2900", LocalDate.parse("2023-01-01"), LocalDate.parse("2024-01-01"))
    }
    @Test
    fun eventSummaryForConstituency() {
        val x = tw.eventSummaryForConstituency(ConstituencyName.of("Aldershot"), LocalDate.parse("2023-01-01"), LocalDate.parse("2024-01-01"))
    }

    @Test
    fun rightNow() {
        val x = tw.overflowingRightNow()

        expectThat(x).size.isGreaterThan(1)
    }

    @Test
    fun haveLiveData() {
        expectThat(tw.haveLiveDataFor()).contains(ConstituencyName.of("Hertsmere"))
        expectThat(tw.haveLiveDataFor()).not().contains(ConstituencyName.of("Liverpool, Riverside"))
    }
}