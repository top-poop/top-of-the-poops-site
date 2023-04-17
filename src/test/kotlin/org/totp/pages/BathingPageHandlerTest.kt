package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.BathingName
import org.totp.model.data.BathingCSO
import org.totp.model.data.BathingRank
import org.totp.model.data.BeachName
import org.totp.model.data.CompanyName
import org.totp.model.data.ConstituencyName
import org.totp.model.data.Coordinates
import org.totp.model.data.WaterwayName
import java.time.Duration

class BathingPageHandlerTest {


    val service = routes(
        "/{bathing}" bind Method.GET to BathingPageHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            bathingRankings = { listOf(
                BathingRank(
                1,
                BathingName.of("bob"),
                CompanyName.of("company"),
                10,
                Duration.ofHours(1),
                DeltaValue.of(10),
                Duration.ofSeconds(11)
            )
            ) },
            bathingCSOs = {
                listOf(
                    BathingCSO(
                        2022, CompanyName.of("company"),
                        sitename = "site name",
                        bathing = BathingName.of("bathing"),
                        count = 10,
                        duration = Duration.ofHours(10),
                        reporting = 19.2,
                        waterway = WaterwayName.of("waterway"),
                        location = Coordinates(10, -10),
                        constituency = ConstituencyName.of("constituency"),
                        beach = BeachName.of("beach")
                    )
                )
            },
            beachBoundaries = { null }
        )
    )

    @Test
    fun `renders a bathing area`() {
        Html(service(Request(Method.GET, "/bob").header("host", "bob.com")))
    }
}