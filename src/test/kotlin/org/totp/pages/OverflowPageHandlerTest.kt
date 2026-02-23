package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.testing.RecordingEvents
import org.junit.jupiter.api.Test
import org.totp.db.*
import org.totp.model.TotpHandlebars
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.Month

class OverflowPageHandlerTest {

    val clock = Clock.systemUTC()

    val events = RecordingEvents()

    val connection = HikariWithConnection(lazy { datasource() })

    val stream = StreamData(events, connection)

    val service = routes(
        "/{id}" bind Method.GET to OverflowPageHandler(
            clock = clock,
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            stream = stream,
            annualSewageRainfall = { _, _, _, _ ->
                AnnualSewageRainfall(
                    2025, listOf(
                        MonthlySewageRainfall(
                            Month.JANUARY, days = listOf(
                                DailySewageRainfall(
                                    date = LocalDate.parse("2025-01-01"),
                                    start = Duration.ofMinutes(72),
                                    offline = Duration.ofMinutes(72),
                                    potential = Duration.ofMinutes(72),
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
    fun `renders an overflow`() {
        val html = Html(
            service(
                Request(Method.GET, "/AWS00532")
                    .header("host", "bob.com")
            )
        )
    }

    @Test
    fun `renders an overflow in previous year`() {
        val html = Html(
            service(
                Request(Method.GET, "/AWS00532")
                    .query("year", "2025")
                    .header("host", "bob.com")
            )
        )
    }
}