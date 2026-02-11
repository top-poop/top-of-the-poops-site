package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.db.StreamData
import org.totp.model.TotpHandlebars
import org.totp.model.data.*
import java.time.Clock
import java.time.Duration

class ConstituencyLivePageHandlerTest {

    val clock = Clock.systemUTC()

    val service = routes(
        "/{constituency}" bind Method.GET to ConstituencyLivePageHandler(
            clock = clock,
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            constituencyBoundary = { GeoJSON.of("some geojson") },
            mpFor = {
                anMP
            },
            constituencyLiveAvailable = { setOf(ConstituencyName("Aldershot")) },
            constituencyNeighbours = { listOf(ConstituencyName("c"), ConstituencyName("d")) },
            constituencyLiveTotals = { c, _, _ ->
                StreamData.ConstituencyLiveTotal(
                    constituency = c,
                    duration = Duration.ofHours(10),
                    csoCount = 10
                )
            },
            liveDataLatest = clock::instant,
            csoLive = { c, _, _ -> listOf() }
        )
    )

    @Test
    fun `renders a constituency`() {
        val html = Html(service(Request(Method.GET, "/aldershot").header("host", "bob.com")))
    }
}