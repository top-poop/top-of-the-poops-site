package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.SeneddConstituencyName
import strikt.api.expectThat
import strikt.assertions.isNotEmpty
import java.time.Duration


class SeneddConstituenciesPageHandlerTest {

    val a = SeneddConstituencyName.of("a")
    val b = SeneddConstituencyName.of("b")
    val service = routes(
        "/" bind Method.GET to SeneddConstituenciesPageHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            seneddConstituencies = {
                listOf(
                    SeneddConstituencyRank(
                        1,
                        a,
                        100,
                        Duration.ofHours(1),
                        25,
                        Duration.ofHours(10), csoCount = 20,
                    ),
                    SeneddConstituencyRank(
                        2,
                        b,
                        2,
                        Duration.ofHours(2),
                        50,
                        Duration.ofHours(20), csoCount = 324,
                    )
                )
            },
        )
    )

    @Test
    fun `renders the senedd constituency page`() {
        val html = Html(service(Request(Method.GET, "/")))
        expectThat(html).select(".footer").isNotEmpty()
    }
}