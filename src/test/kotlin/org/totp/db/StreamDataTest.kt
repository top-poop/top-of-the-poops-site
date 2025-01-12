package org.totp.db

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isGreaterThan
import strikt.assertions.size

class StreamDataTest {


    val connection = HikariWithConnection(lazy { datasource() })

    val stream = StreamData(connection)

    @Test
    fun rightNow() {
        val x = stream.overflowingRightNow()

        expectThat(x).size.isGreaterThan(1)
    }

    @Test
    fun liveSummary() {
        val x = stream.summary()

        expectThat(x.companies).size.isGreaterThan(1)

        print(x)
    }


}