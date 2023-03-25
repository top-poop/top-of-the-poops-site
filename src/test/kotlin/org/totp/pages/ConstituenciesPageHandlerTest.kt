package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.ConstituencyName
import strikt.api.expectThat
import strikt.assertions.isNotEmpty
import java.time.Duration


class ConstituenciesPageHandlerTest {

    val service = routes(
        "/" bind Method.GET to ConstituenciesPageHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            consituencyRankings = {
                listOf(
                    ConstituencyRank(
                        1,
                        ConstituencyName("a"),
                        Uri.of("/con/1"),
                        MP("mp1", "con", "handle1", Uri.of("https://example.com/1")),
                        100,
                        Duration.ofHours(1),
                        25,
                        Duration.ofHours(10)
                    ),
                    ConstituencyRank(
                        2,
                        ConstituencyName("b"),
                        Uri.of("/con/2"),
                        MP("mp2", "noc", "handle2", Uri.of("https://example.com/2")),
                        2,
                        Duration.ofHours(2),
                        50,
                        Duration.ofHours(20)
                    )
                )
            },
        )
    )

    @Test
    fun `renders the constituency page`() {
        val html = Html(service(Request(Method.GET, "/")))
        expectThat(html).select("#footer").isNotEmpty()
    }
}