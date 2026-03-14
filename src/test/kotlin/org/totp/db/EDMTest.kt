package org.totp.db

import org.junit.jupiter.api.Test
import org.totp.model.data.ConstituencyName
import org.totp.model.data.SeneddConstituencyName
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import strikt.assertions.isLessThan
import strikt.assertions.size

class EDMTest {

    val tw = EDM(testDbConnection)

    @Test
    fun `annual summary for westminster constituency`() {
        val summaries = tw.annualSummariesForConstituency(ConstituencyName("Aldershot"))

        expectThat(summaries).size.isEqualTo(5)
        expectThat(summaries.maxOf { it.spills }).isLessThan(100)
        expectThat(summaries.maxOf { it.spills }).isGreaterThan(0)
    }

    @Test
    fun `annual summary for senedd constituency`() {
        val summaries = tw.annualSummariesForSeneddConstituency(SeneddConstituencyName("Ceredigion Penfro"))

        expectThat(summaries).size.isEqualTo(5)
        expectThat(summaries.maxOf { it.spills }).isLessThan(20_000)
        expectThat(summaries.maxOf { it.spills }).isGreaterThan(5_000)
    }

    @Test
    fun annualSummaryForPlaceWithNoCSOs() {
        val summaries = tw.annualSummariesForConstituency(ConstituencyName("Islington North"))

        expectThat(summaries).size.isEqualTo(5)
        expectThat(summaries.maxOf { it.spills }).isEqualTo(0)
    }
}