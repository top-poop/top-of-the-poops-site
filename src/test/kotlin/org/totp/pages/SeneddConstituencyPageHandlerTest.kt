package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.*
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.time.Clock
import java.time.Duration

class SeneddConstituencyPageHandlerTest {

    val clock = Clock.systemUTC()

    var summaries = listOf(
        CSOTotals(
            constituency = ConstituencyName("Your House"),
            senedd = SeneddConstituencyName.Companion.of("Senedd"),
            places = listOf(PlaceName.Companion.of("v")),
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
        "/{constituency}" bind Method.GET to SeneddConstituencyPageHandler(
            clock = clock,
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            constituencySpills = { summaries },
            constituencyBoundary = { GeoJSON.of("some geojson") },
            constituencyNeighbours = { listOf(SeneddConstituencyName("c"), SeneddConstituencyName("d")) },
            constituencyRank = {
                SeneddConstituencyRank(
                    1,
                    SeneddConstituencyName("Ceredigion Penfro"),
                    10,
                    Duration.ofHours(1),
                    20,
                    Duration.ZERO,
                    csoCount = 10
                )
            },
            westminsterConstituencies = { listOf(ConstituencyName.of("Aldershot")) },
            mpFor = {
                anMP
            },
            westminsterConstituencyRank = {
                ConstituencyRank(
                    1,
                    ConstituencyName("Aldershot"),
                    10,
                    Duration.ofHours(1),
                    20,
                    Duration.ZERO,
                    csoCount = 10
                )
            },

            )
    )

    @Test
    fun `renders a constituency`() {
        val html = Html(service(Request(Method.GET, "/ceredigion-penfro").header("host", "bob.com")))

        expectThat(html.selectFirst("meta[name='twitter:image']")!!.attr("content"))
            .isEqualTo("https://top-of-the-poops.org/badges/senedd-constituency/ceredigion-penfro-2024.png")

        expectThat(html.attributeText("link[rel='canonical']", "href"))
            .isEqualTo("http://bob.com/ceredigion-penfro")
    }
}