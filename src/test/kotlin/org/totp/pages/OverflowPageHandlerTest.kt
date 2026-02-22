package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.testing.RecordingEvents
import org.junit.jupiter.api.Test
import org.totp.db.HikariWithConnection
import org.totp.db.StreamData
import org.totp.db.datasource
import org.totp.model.TotpHandlebars
import java.time.Clock

class OverflowPageHandlerTest {

    val clock = Clock.systemUTC()

    val events = RecordingEvents()

    val connection = HikariWithConnection(lazy { datasource() })

    val stream = StreamData(events, connection)

    val service = routes(
        "/{id}" bind Method.GET to OverflowPageHandler(
            clock = clock,
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            stream = stream
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