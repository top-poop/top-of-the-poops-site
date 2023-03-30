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
import org.totp.model.TotpHandlebars
import org.totp.model.data.CSO
import org.totp.model.data.CSOTotals
import org.totp.model.data.CompanyName
import org.totp.model.data.ConstituencyName
import org.totp.model.data.Coordinates
import org.totp.model.data.WaterwayName
import org.totp.model.data.waterwayCSOs
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import java.time.Duration

class WaterwayPageHandlerTest {

    var summaries = listOf(
        CSOTotals(
            constituency = ConstituencyName("Your House"),
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
        "/{company}/{waterway}" bind Method.GET to WaterwayPageHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            waterwaySpills = waterwayCSOs { summaries },
        )
    )

    @Test
    fun `renders a waterway`() {
        Html(service(Request(Method.GET, "/venture-cap/your-river")))
    }

    @Test
    fun `renders a river with no overflows`() {
        // surprisingly there are one or two
        summaries = listOf()
        Html(service(Request(Method.GET, "/venture-cap/your-river")))
    }
}