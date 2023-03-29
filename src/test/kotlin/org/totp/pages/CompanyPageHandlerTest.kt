package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.CompanyName
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
                        Duration.ofHours(10),
                        4321
                    )
                )
            },
            waterCompanies = { listOf(aWaterCompany) }
        )
    )

    @Test
    fun `renders the company  page`() {
        val html = Html(service(Request(Method.GET, "/water-co")))
    }
}