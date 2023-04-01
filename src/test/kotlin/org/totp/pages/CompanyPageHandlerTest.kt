package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.CompanyName
import strikt.api.expectThat
import strikt.assertions.contains
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
            beachRankings = { listOf(aBeach) },
            riverRankings = { listOf(aRiver(1)) }
        )
    )

    @Test
    fun `renders the company  page`() {

        print(File(".").absolutePath)

        val response = service(Request(Method.GET, "/water-co"))
        val html = Html(response)

        expectThat(response.bodyString()) {
            contains("1,234")
            contains("2020")
            contains("That's 4.167 days")
            contains("On average 3.381")
        }
    }
}