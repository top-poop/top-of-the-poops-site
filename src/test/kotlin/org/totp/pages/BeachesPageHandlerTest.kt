package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.BeachRank
import org.totp.model.data.CompanyName
import java.time.Duration


class BeachesPageHandlerTest {

    val service = routes(
        "/" bind Method.GET to BeachesPageHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            beachRankings = {
                listOf(
                    BeachRank(
                        1,
                        "beach",
                        CompanyName.of("company"),
                        10,
                        Duration.ofHours(1)
                    )
                )
            },
        )
    )

    @Test
    fun `renders the beaches  page`() {
        val html = Html(service(Request(Method.GET, "/")))
    }
}