package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.GeoJSON
import org.totp.model.data.LocalityName
import java.time.Duration


class BadgesLocalitiesHandlerTest {

    val a = LocalityName("a")
    val b = LocalityName("b")


    val service = routes(
        "/{letter}" bind Method.GET to BadgesLocalitiesHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            localityRankings = {
                listOf(
                    LocalityRank(
                        rank = 1,
                        csoCount = 10,
                        localityName = a,
                        overflowCount = 100,
                        duration = Duration.ofHours(1),
                        countDelta = 25,
                        durationDelta = Duration.ofHours(10),
                        zeroMonitoringCount = 1,
                    ),
                    LocalityRank(
                        rank = 2,
                        csoCount = 20,
                        localityName = b,
                        overflowCount = 2,
                        duration = Duration.ofHours(2),
                        countDelta = 50,
                        durationDelta = Duration.ofHours(20),
                        zeroMonitoringCount = 0
                    )
                )
            },
            localityBoundaries = {
                GeoJSON.of("{}")
            }
        )
    )

    @Test
    fun `renders the badges  page`() {
        val html = Html(service(Request(Method.GET, "/a")))
    }
}