package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.ConstituencyName
import java.time.Duration


class BadgesHomeHandlerTest {

    val service = routes(
        "/" bind Method.GET to BadgesHomeHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            constituencyRankings = {
                listOf(
                    ConstituencyRank(
                        1,
                        ConstituencyName("a"),
                        100,
                        Duration.ofHours(1),
                        25,
                        Duration.ofHours(10)
                    )
                )
            },
            beachRankings = {
                listOf(aBeach)
            }
        )
    )

    @Test
    fun `renders the badges home page`() {
        val html = Html(service(Request(Method.GET, "/")))

        //expectThat(html).twitterImageUri().isEqualTo("https://top-of-the-poops.org/badges/home.png")
    }
}