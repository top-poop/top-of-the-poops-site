package org.totp.db

import org.http4k.core.Uri
import org.http4k.testing.RecordingEvents
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.RepeatedTest
import org.totp.memoize
import org.totp.model.data.CompanyAnnualSummaries
import org.totp.pages.uriHavingFileContent
import java.io.File
import java.time.Clock

class UnifiedAnnualDataTest {
    @RepeatedTest(5)
    fun `bodge together the two data sources`() {

        val annual = unifiedAnnualData.unified()

        print(annual)
    }

    companion object {

        private lateinit var unifiedAnnualData: UnifiedAnnualData

        @JvmStatic
        @BeforeAll
        fun setup() {
            val events = RecordingEvents()

            val clock = Clock.systemUTC()

            val stream = StreamData(events, testDbConnection)

            val remote = uriHavingFileContent(
                Uri.of("spills-by-company.json"),
                File("services/data/datafiles/v1/2024/spills-by-company.json")
            )
            val companyAnnualSummaries = memoize(CompanyAnnualSummaries(remote))
            unifiedAnnualData = UnifiedAnnualData(clock, stream, companyAnnualSummaries)
        }
    }
}