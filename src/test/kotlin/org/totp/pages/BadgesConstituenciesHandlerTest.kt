package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.ConstituencyName
import org.totp.model.data.GeoJSON
import java.time.Duration


class BadgesConstituenciesHandlerTest {

    val a = ConstituencyName("a")
    val b = ConstituencyName("b")


    val service = routes(
        "/{letter}" bind Method.GET to BadgesConstituenciesHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            constituencyRankings = {
                listOf(
                    ConstituencyRank(
                        1,
                        a,
                        100,
                        Duration.ofHours(1),
                        25,
                        Duration.ofHours(10)
                    ),
                    ConstituencyRank(
                        2,
                        b,
                        2,
                        Duration.ofHours(2),
                        50,
                        Duration.ofHours(20)
                    )
                )
            },
            mpFor = {
                MP("mp2", "noc", "handle2", Uri.of("https://example.com/2"))
            },
            constituencyBoundaries = {
                GeoJSON.of("{}")
            }
        )
    )

    @Test
    fun `renders the badges  page`() {
        val html = Html(service(Request(Method.GET, "/a")))
    }
}