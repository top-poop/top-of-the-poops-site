package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.CompanyName
import org.totp.model.data.RiverRank
import org.totp.model.data.WaterwayName
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.size
import java.time.Duration


class RiversPageHandlerTest {

    val service = routes(
        "/" bind Method.GET to RiversPageHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            riverRankings = {
                listOf(
                    RiverRank(1, WaterwayName("river"), CompanyName("company"), 10, Duration.ofHours(1)),
                    RiverRank(2, WaterwayName("river2"), CompanyName("company2"), 11, Duration.ofHours(1)),
                )
            },
        )
    )

    @Test
    fun `renders the rivers  page`() {
        val html = Html(service(Request(Method.GET, "/")))

        expectThat(html.select("#table-river tbody tr")).size.isEqualTo(2)
    }
}