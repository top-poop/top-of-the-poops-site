package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.*
import java.time.Duration

class PlacePageHandlerTest {

    var summaries = listOf(
        CSOTotals(
            constituency = ConstituencyName("Your House"),
            places = listOf(PlaceName.of("v")),
            cso = CSO(
                company = CompanyName.of("Venture Cap"),
                sitename = "Your House",
                waterway = WaterwayName("Your River"),
                location = Coordinates(lon = -0.12460789, lat = 51.49993385),
            ),
            count = 100,
            duration = Duration.ofHours(100),
            reporting = 0.0f
        )
    )

    val service = routes(
        "/{place}" bind Method.GET to PlacePageHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            placeTotals = { summaries },
            placeBoundary = { GeoJSON.of("some geojson") },
            placeRank = {
                PlaceRank(
                    1,
                    PlaceName("Aldershot"),
                    10,
                    zeroMonitoringCount = 10,
                    Duration.ofHours(1),
                    20,
                    Duration.ZERO,
                    csoCount = 100,
                )
            },
            placeRivers = { listOf(aRiver(1)) },
        )
    )

    @Test
    fun `renders a place`() {
        val html = Html(service(Request(Method.GET, "/york").header("host", "bob.com")))
    }
}