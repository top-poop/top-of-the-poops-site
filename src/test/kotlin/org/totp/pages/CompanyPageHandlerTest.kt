package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.BathingName
import org.totp.model.data.BathingRank
import org.totp.model.data.CompanyName
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.one
import java.io.File
import java.time.Duration


class CompanyPageHandlerTest {

    val service = routes(
        "/{company}" bind Method.GET to CompanyPageHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            companySummaries = {
                listOf(
                    CompanyAnnualSummary(
                        CompanyName.of("Water Co"),
                        2020,
                        1234,
                        Duration.ofHours(100),
                        4321
                    )
                )
            },
            waterCompanies = { listOf(aWaterCompany) },
            bathingRankings = {
                listOf(
                    BathingRank(
                        1,
                        BathingName.of("beach"),
                        CompanyName.of("Water Co"),
                        10,
                        Duration.ofHours(1),
                        DeltaValue.of(10),
                        Duration.ofSeconds(11)
                    )
                )
            },
            riverRankings = { listOf(aRiver(1)) }
        )
    )

    @Test
    fun `renders the company  page`() {

        print(File(".").absolutePath)

        val response = service(Request(Method.GET, "/water-co"))

        val html = Html(response)

        expectThat(html).twitterImageUri()
            .isEqualTo("https://top-of-the-poops.org/badges/company/water-co.png")

        expectThat(html.select("h3").map { it.text() })
            .one { isEqualTo("Rivers Polluted by Water Co") }
            .one { isEqualTo("Beaches Polluted by Water Co") }

        expectThat(response.bodyString()) {
            contains("1,234")
            contains("2020")
            contains("That's 4.17 days")
            contains("On average 3.4")
        }
    }
}