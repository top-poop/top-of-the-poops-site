package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.*
import java.time.Duration

class PlacesPageHandlerTest {

    val service = routes(
        "/" bind Method.GET to PlacesPageHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            areaRankings = {
                listOf(
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
                )
            }
        )
    )

    @Test
    fun `renders places`() {
        val html = Html(service(Request(Method.GET, "/").header("host", "bob.com")))
    }
}