package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.BathingCSO
import org.totp.model.data.BathingName
import org.totp.model.data.BathingRank
import org.totp.model.data.BeachName
import org.totp.model.data.CompanyName
import org.totp.model.data.ConstituencyName
import org.totp.model.data.Coordinates
import org.totp.model.data.GeoJSON
import org.totp.model.data.WaterwayName
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.time.Duration

class BathingPageHandlerTest {


    val service = routes(
        "/{bathing}" bind Method.GET to BathingPageHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            bathingRankings = {
                listOf(
                    BathingRank(
                        1,
                        BathingName.of("bob"),
                        CompanyName.of("company"),
                        10,
                        Duration.ofHours(1),
                        DeltaValue.of(10),
                        Duration.ofSeconds(11),
                        loc = Coordinates(1.0,1.0),
                        geo = GeoJSON(""),
                    )
                )
            },
            bathingCSOs = {
                listOf(
                    BathingCSO(
                        2024, CompanyName.of("company"),
                        sitename = "site name",
                        bathing = BathingName.of("bathing"),
                        count = 10,
                        duration = Duration.ofHours(10),
                        reporting = 19.2,
                        waterway = WaterwayName.of("waterway"),
                        location = Coordinates(10.0, -10.0),
                        constituency = ConstituencyName.of("constituency"),
                        beach = BeachName.of("beach")
                    )
                )
            },
            beachBoundaries = { null },
            mpFor = { MP("mp", "party", "example", Uri.of("http://example.com")) },
            constituencyRank = { null }
        )
    )

    @Test
    fun `renders a bathing area`() {
        Html(service(Request(Method.GET, "/bob").header("host", "bob.com")))
    }


    @Test
    fun `possible beach names from composite bathing name`() {

        expectThat(possibleBeachesFromBathingName(BathingName("COWES, GURNARD")))
            .isEqualTo(listOf(BeachName("COWES, GURNARD"), BeachName("COWES"), BeachName("GURNARD")))

        expectThat(possibleBeachesFromBathingName(BathingName("Southport, St Annes Pier, St. Annes North and Blackpool South")))
            .isEqualTo(
                listOf(
                    BeachName("Southport, St Annes Pier, St. Annes North and Blackpool South"),
                    BeachName("Southport"),
                    BeachName("St Annes Pier"),
                    BeachName("St. Annes North"),
                    BeachName("Blackpool South"),
                )
            )
    }


}



