package org.totp.db

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry.writing
import org.totp.model.data.CompanyName
import org.totp.pages.CompanyAnnualSummary
import org.totp.pages.Source
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class UnifiedAnnualData(
    private val clock: Clock,
    private val streamData: StreamData,
    private val eaAnnualTotals: () -> List<CompanyAnnualSummary>
) {

    val cache = Caffeine.newBuilder()
        .expireAfter(writing({ k, _ ->
            if (k == LocalDate.now(clock).year) {
                Duration.ofMinutes(5)
            } else {
                Duration.ofHours(48)
            }
        }))
        .build<Int, List<CompanyAnnualSummary>>()

    fun unified(): List<CompanyAnnualSummary> {

        val currentYear = LocalDate.ofInstant(clock.instant(), ZoneId.systemDefault()).year

        val years = 2025..currentYear
        val liveDataTotals = years.flatMap(::streamTotalsForYear)

        return (eaAnnualTotals() + liveDataTotals).sortedWith(compareBy<CompanyAnnualSummary> { it.year }.thenBy { it.name })
    }

    private fun streamTotalsForYear(year: Int): List<CompanyAnnualSummary> {
        return cache.get(year) {
            val start = LocalDate.of(year, 1, 1)
            val end = LocalDate.of(year, 12, 31)
            streamData.totalsByCompany(start, end)
                .map {
                    CompanyAnnualSummary(
                        source = Source.Stream,
                        it.company.asCompanyName() ?: CompanyName.of("Unknown"),
                        year,
                        0,
                        it.bucket.start,
                        0
                    )
                }
        }
    }
}