package org.totp.db

import org.junit.jupiter.api.Test
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
}