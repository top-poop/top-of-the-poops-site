package org.totp.pages

import com.github.jknack.handlebars.io.StringTemplateSource
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.strikt.header
import org.http4k.strikt.status
import org.junit.jupiter.api.Test
import org.totp.db.StreamData
import org.totp.model.TotpHandlebars
import org.totp.model.data.*
import org.totp.model.data.localityCSOs
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import java.time.Clock
import java.time.Duration

class LocalityPageHandlerTest {

    val clock = Clock.systemUTC()

    var summaries = listOf(
        CSOTotals(
            constituency = ConstituencyName("Your House"),
            localities = listOf(LocalityName.Companion.of("v")),
            cso = CSO(
                company = CompanyName.Companion.of("Venture Cap"),
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
        "/{locality}" bind Method.GET to LocalityPageHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            localityTotals = { summaries },
            localityBoundary = { GeoJSON.Companion.of("some geojson") },
            localityRank = {
                LocalityRank(
                    1,
                    LocalityName("Aldershot"),
                    10,
                    zeroMonitoringCount = 10,
                    Duration.ofHours(1),
                    20,
                    Duration.ZERO,
                    csoCount = 100,
                )
            },
            localityRivers = { listOf(aRiver(1)) },
        )
    )

    @Test
    fun `renders a place`() {
        val html = Html(service(Request.Companion(Method.GET, "/york").header("host", "bob.com")))
    }
}