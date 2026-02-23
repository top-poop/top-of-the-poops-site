package org.totp.db

import org.http4k.testing.RecordingEvents
import org.junit.jupiter.api.Test
import org.totp.model.data.ConstituencyName
import org.totp.model.data.StreamCompanyName
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import strikt.assertions.isNotNull
import strikt.assertions.size
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.Month

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
    fun csoSummaryForConstituency() {
        val result = stream.byCsoForConstituency(
            ConstituencyName.of("Aldershot"), LocalDate.parse("2025-01-01"), LocalDate.now()
        )
        expectThat(result).size.isGreaterThan(1)
    }

    @Test
    fun latest() {
        stream.latestAvailable()
    }

    @Test
    fun infrastructureSummary() {
        stream.infrastructureSummary(StreamCompanyName("Anglian"))
    }

    @Test
    fun haveLiveDataForConstituencies() {
        expectThat(stream.haveLiveDataForConstituencies()).size.isGreaterThan(1)
    }

    @Test
    fun haveLiveDataForCompanies() {
        expectThat(stream.haveLiveDataForCompanies()).size.isGreaterThan(1)
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

    @Test
    fun eventsForCso() {
        expectThat(stream.eventsForCso(
            StreamId.of("AWS00532"),
            start = LocalDate.parse("2026-01-01"),
            end = LocalDate.parse("2026-02-01")
        )).size.isGreaterThan(1)
    }

    @Test
    fun eventSummaryForCso() {
        val buckets = stream.eventSummaryForCso(
            StreamId.of("AWS00532"),
            current = LocalDate.now(),
            start = LocalDate.parse("2026-01-01"),
            end = LocalDate.parse("2026-02-01")
        )

        expectThat(buckets).size.isGreaterThan(1)
    }

    @Test
    fun csoMonthlyBuckets() {
        val buckets = stream.csoMonthlyBuckets(
            StreamId.of("AWS00532"),
            current = LocalDate.now(),
            start = LocalDate.parse("2026-01-01"),
            end = LocalDate.parse("2027-01-01")
        )

        expectThat(buckets).size.isEqualTo(12)
    }

    @Test
    fun cso() {
        val buckets = stream.cso(
            StreamId.of("AWS00532"),
            start = LocalDate.parse("2026-01-01"),
            end = LocalDate.parse("2027-01-01")
        )

        expectThat(buckets).isNotNull()
    }

    @Test
    fun `cso with no summary rows`() {
        val buckets = stream.cso(
            StreamId.of("SWS00014"),
            start = LocalDate.parse("2026-01-01"),
            end = LocalDate.parse("2027-01-01")
        )

        expectThat(buckets).isNotNull()
    }

    @Test
    fun daily() {
        val daily = stream.dailyByConstituency(ConstituencyName.of("Aldershot"), start= LocalDate.parse("2025-01-01"), end= LocalDate.parse("2026-01-01"))
        expectThat(daily).size.isGreaterThan(0)
    }

    @Test
    fun annualdata() {

        val ea = EnvironmentAgency(connection)

        val annual = AnnualLiveSewage(ea, stream)

        val result = annual.byConstituency(ConstituencyName.of("Aldershot"), start= LocalDate.parse("2025-01-01"), end= LocalDate.parse("2026-01-01"))

        expectThat(result.year).isEqualTo(2025)
        expectThat(result.months).size.isEqualTo(12)
        expectThat(result.months.first().month).isEqualTo(Month.JANUARY)
        expectThat(result.months.first().days.size).isEqualTo(31)

        expectThat(result.totalDuration()).isGreaterThan(Duration.ZERO)

    }

}