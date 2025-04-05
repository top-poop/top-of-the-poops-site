package org.totp.db

import org.http4k.testing.RecordingEvents
import org.junit.jupiter.api.Test
import org.totp.model.data.ConstituencyName
import strikt.api.expectThat
import strikt.assertions.isGreaterThan
import strikt.assertions.size
import java.time.Clock
import java.time.LocalDate

class StreamDataTest {

    val events = RecordingEvents()

    val clock = Clock.systemUTC()

    val connection = HikariWithConnection(lazy { datasource() })

    val stream = StreamData(events, connection)

    @Test
    fun eventSummaryForConstituency() {
        expectThat(
            stream.eventSummaryForConstituency(
                ConstituencyName.of("Aldershot"), LocalDate.parse("2025-01-01"), LocalDate.now()
            )
        ).size.isGreaterThan(1)
    }

    @Test
    fun latest() {
        stream.latestAvailable()
    }

    @Test
    fun haveLiveDataFor() {
        expectThat(stream.haveLiveDataFor()).size.isGreaterThan(1)
    }

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