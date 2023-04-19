package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.CompanyName
import org.totp.model.data.ConstituencyName
import org.totp.model.data.Coordinates
import org.totp.model.data.ShellfishCSO
import org.totp.model.data.ShellfishRank
import org.totp.model.data.ShellfisheryName
import org.totp.model.data.WaterwayName
import java.time.Duration

class ShellfisheryPageHandlerTest {

    val service = routes(
        "/{area}" bind Method.GET to ShellfisheryPageHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            shellfishSpills = {
                listOf(
                    ShellfishCSO(
                        2022, CompanyName.of("Company"),
                        sitename = "site name",
                        shellfishery = ShellfisheryName.of("shellfishery"),
                        count = 10,
                        duration = Duration.ofHours(22),
                        reporting = 23.3,
                        waterway = WaterwayName.of("waterway"),
                        location = Coordinates(22, 33),
                        constituency = ConstituencyName.of("Constituency")
                    )
                )
            },
            shellfishRankings = {
                listOf(
                    ShellfishRank(
                        1,
                        ShellfisheryName.of("shellfishery"),
                        CompanyName.of("water co"),
                        count = 10,
                        duration = Duration.ofHours(1),
                        countDelta = DeltaValue.of(10),
                        durationDelta = Duration.ofHours(100)
                    )
                )
            },
            mpFor = { MP("mp", "party", "example", Uri.of("http://example.com")) },
            constituencyRank = { null }
        )
    )

    @Test
    fun `renders a shellfish area`() {
        Html(service(Request(Method.GET, "/shellfishery").header("host", "bob.com")))
    }
}



