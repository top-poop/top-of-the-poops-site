package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.BeachRank
import org.totp.model.data.CompanyName
import org.totp.model.data.ConstituencyContact
import org.totp.model.data.ConstituencyName
import org.totp.model.data.GeoJSON
import java.time.Duration


class BadgesCompaniesHandlerTest {


    val service = routes(
        "/" bind Method.GET to BadgesCompaniesHandler(
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
        )
    )

    @Test
    fun `renders the companies badges  page`() {
        val html = Html(service(Request(Method.GET, "/")))
    }
}