package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.GeoJSON
import org.totp.model.data.PlaceName
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.isEqualTo
import java.time.Duration


class BadgesPlacesHandlerTest {

    val a = PlaceName("a")
    val b = PlaceName("b")


    val service = routes(
        "/{letter}" bind Method.GET to BadgesPlacesHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            placeRankings = {
                listOf(
                    PlaceRank(
                        rank = 1,
                        csoCount = 10,
                        placeName = a,
                        overflowCount = 100,
                        duration = Duration.ofHours(1),
                        countDelta = 25,
                        durationDelta = Duration.ofHours(10),
                        zeroMonitoringCount = 1,
                    ),
                    PlaceRank(
                        rank = 2,
                        csoCount = 20,
                        placeName = b,
                        overflowCount = 2,
                        duration = Duration.ofHours(2),
                        countDelta = 50,
                        durationDelta = Duration.ofHours(20),
                        zeroMonitoringCount = 0
                    )
                )
            },
            placeBoundaries = { name ->
                GeoJSON.of("geojson-${name.value}")
            }
        )
    )

    @Test
    fun `renders the badges  page`() {
        val html = Html(service(Request(Method.GET, "/a")))

        expectThat(html)
            .select("#a-2025")
            .first()
            .attribute("data-name").isEqualTo("a")

        expectThat(html)
            .select("#boundary-a")
            .first()
            .text().isEqualTo("geojson-a")
    }
}