package org.totp.db

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isGreaterThan
import strikt.assertions.size
import java.time.Clock

class StreamDataTest {


    val clock = Clock.systemUTC()

    val connection = HikariWithConnection(lazy { datasource() })

    val stream = StreamData(connection)

    @Test
    fun rightNow() {
        val x = stream.overflowingAt(clock.instant())

        expectThat(x).size.isGreaterThan(1)
    }

    @Test
    fun liveSummary() {
        val x = stream.summary()

        expectThat(x.companies).size.isGreaterThan(1)

        print(x)
    }


}