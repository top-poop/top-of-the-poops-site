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
import org.totp.model.TotpHandlebars
import org.totp.model.data.ConstituencyName
import org.totp.model.data.GeoJSON
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
                    duration = Duration.ofHours(10),
                    csoCount = 10
                )
            },
            liveDataLatest = clock::instant,
            csoLive = { c, _, _ -> listOf() },
            annualSewageRainfall = { c, _, _ -> AnnualSewageRainfall(2025, listOf(
                MonthlySewageRainfall(Month.JANUARY, days =  listOf(
                    DailySewageRainfall(date = LocalDate.parse("2025-01-01"), Duration.ofMinutes(72), count = 10, rainfall =  100.0)
                ))
            )) }
        )
    )

    @Test
    fun `renders a constituency`() {
        val html = Html(service(Request(Method.GET, "/aldershot").header("host", "bob.com")))
    }
}