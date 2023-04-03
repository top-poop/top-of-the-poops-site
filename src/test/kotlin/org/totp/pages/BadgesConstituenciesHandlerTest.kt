package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.BeachRank
import org.totp.model.data.CompanyName
import org.totp.model.data.ConstituencyContact
import org.totp.model.data.ConstituencyName
import org.totp.model.data.GeoJSON
import java.time.Duration


class BadgesConstituenciesHandlerTest {

    val a = ConstituencyName("a")
    val b = ConstituencyName("b")


    val service = routes(
        "/" bind Method.GET to BadgesConstituenciesHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            constituencyRankings = {
                listOf(
                    ConstituencyRank(
                        1,
                        a,
                        Uri.of("/con/1"),
                        100,
                        Duration.ofHours(1),
                        25,
                        Duration.ofHours(10)
                    ),
                    ConstituencyRank(
                        2,
                        b,
                        Uri.of("/con/2"),
                        2,
                        Duration.ofHours(2),
                        50,
                        Duration.ofHours(20)
                    )
                )
            },
            constituencyContacts = {
                listOf(
                    ConstituencyContact(a, MP("mp1", "con", "handle1", Uri.of("https://example.com/1"))),
                    ConstituencyContact(b, MP("mp2", "noc", "handle2", Uri.of("https://example.com/2")))
                )
            },
            constituencyBoundaries = {
                GeoJSON.of("{}")
            }
        )
    )

    @Test
    fun `renders the badges  page`() {
        val html = Html(service(Request(Method.GET, "/")))
    }
}