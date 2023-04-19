package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.CompanyName
import org.totp.model.data.RiverRank
import org.totp.model.data.ShellfishRank
import org.totp.model.data.ShellfisheryName
import org.totp.model.data.WaterwayName
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.size
import java.time.Duration


class ShellfisheriesPageHandlerTest {

    val service = routes(
        "/" bind Method.GET to ShellfisheriesPageHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
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
        )
    )

    @Test
    fun `renders the shellfish  page`() {
        val html = Html(service(Request(Method.GET, "/")))

        expectThat(html.select("#table-shellfish tbody tr")).size.isEqualTo(1)
    }
}