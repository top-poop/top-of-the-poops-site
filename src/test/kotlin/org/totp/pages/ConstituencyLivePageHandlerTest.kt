package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.db.AnnualSewageRainfall
import org.totp.db.DailySewageRainfall
import org.totp.db.MonthlySewageRainfall
import org.totp.db.StreamData
import org.totp.db.StreamId
import org.totp.model.TotpHandlebars
import org.totp.model.data.CompanyName
import org.totp.model.data.ConstituencyName
import org.totp.model.data.Coordinates
import org.totp.model.data.GeoJSON
import org.totp.model.data.SiteName
import org.totp.model.data.WaterwayName
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.Month

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
                    start = Duration.ofHours(10),
                    offline = Duration.ofHours(1),
                    potential = Duration.ofHours(2),
                    csoCount = 10,
                )
            },
            liveDataLatest = clock::instant,
            csoLive = { c, _, _ -> listOf(
                StreamData.StreamCsoSummary(
                    CompanyName.of("Thames"),
                    id = StreamId.of("123"),
                    site_name = SiteName.of("site name"),
                    receiving_water = WaterwayName.of("bob"),
                    location = Coordinates(1.23, 4.56),
                    start = Duration.ofMinutes(1),
                    offline = Duration.ofMinutes(2),
                    potential = Duration.ofMinutes(3),
                    days = 24,
                    pcon24nm = ConstituencyName.of("Aldershot")
                )
            ) },
            annualSewageRainfall = { c, _, _ ->
                AnnualSewageRainfall(
                    2025, listOf(
                        MonthlySewageRainfall(
                            Month.JANUARY, days = listOf(
                                DailySewageRainfall(
                                    date = LocalDate.parse("2025-01-01"),
                                    start = Duration.ofMinutes(72),
                                    offline = Duration.ofMinutes(3),
                                    potential = Duration.ofMinutes(15),
                                    count = 10,
                                    rainfall = 100.0
                                )
                            )
                        )
                    )
                )
            }
        )
    )

    @Test
    fun `renders a constituency`() {
        val html = Html(service(Request(Method.GET, "/aldershot").header("host", "bob.com")))
    }
}