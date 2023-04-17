package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import strikt.api.expectThat
import strikt.assertions.isEqualTo


class BeachesPageHandlerTest {

    val service = routes(
        "/" bind Method.GET to BeachesPageHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            bathingRankings = {
                listOf(aBeach)
            },
        )
    )

    @Test
    fun `renders the beaches  page`() {
        val html = Html(service(Request(Method.GET, "/")))

        expectThat(html).twitterImageUri()
            .isEqualTo("https://top-of-the-poops.org/badges/home/beaches.png")
    }
}