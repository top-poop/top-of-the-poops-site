package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.Address
import org.totp.model.data.BeachRank
import org.totp.model.data.ConstituencyName
import org.totp.model.data.ConstituencyRankings
import org.totp.model.data.MediaAppearance
import org.totp.model.data.MediaAppearances
import org.totp.model.data.WaterCompanies
import org.totp.model.data.WaterCompany
import strikt.api.expectThat
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import java.time.Duration
import java.time.LocalDate


class HomepageHandlerTest {

    val service = routes(
        "/" bind Method.GET to HomepageHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            consituencyRankings = {
                listOf(
                    ConstituencyRank(
                        1,
                        ConstituencyName("a"),
                        Uri.of("/con/1"),
                        MP("mp1", "con", "handle1", Uri.of("https://example.com/1")),
                        "company 1",
                        100,
                        Duration.ofHours(1),
                        25,
                        Duration.ofHours(10)
                    ),
                    ConstituencyRank(
                        2,
                        ConstituencyName("b"),
                        Uri.of("/con/2"),
                        MP("mp2", "noc", "handle2", Uri.of("https://example.com/2")),
                        "company 2",
                        2,
                        Duration.ofHours(2),
                        50,
                        Duration.ofHours(20)
                    )
                )
            },
            beachRankings = {
                listOf(BeachRank(1, "beach", "company", 10, Duration.ofHours(1)))
            },
            appearances = {
                listOf(
                    MediaAppearance(
                        title = "Here",
                        publication = "Mag",
                        date = LocalDate.of(1996, 12, 25),
                        uri = Uri.of("http:/example.com"),
                        imageUri = Uri.of("http://example.com/image.jpg")
                    )
                )
            },
            companies = {
                listOf(
                    WaterCompany(
                        "company", Address("1", "2", "3", "town", "postcode"),
                        Uri.of(""), Uri.of("http://example.com/company"), Uri.of("http://example.com/image"), "@bob"
                    )
                )
            }
        )
    )

    @Test
    fun `renders the homepage`() {
        val html = Html(service(Request(Method.GET, "/")))

        expectThat(html).select("#footer").isNotEmpty()
    }


    @Test
    fun `loading media appearances`() {

        val text = """[
  {
    "href": "http://example.com",
    "where": "Some August Publication",
    "title": "Nice Title",
    "date": "2022-01-01",
    "image": "http://example.com/image.jpg"
  }]
"""
        val rankings = MediaAppearances(
            routes("media-appearances.json" bind { _: Request -> Response(Status.OK).body(text) })
        )

        expectThat(rankings()).hasSize(1).and {
            get(0).and {
                get { uri }.isEqualTo(Uri.of("http://example.com"))
                get { publication }.isEqualTo("Some August Publication")
                get { title }.isEqualTo("Nice Title")
                get { date }.isEqualTo(LocalDate.of(2022, 1, 1))
                get { imageUri }.isEqualTo(Uri.of("http://example.com/image.jpg"))
            }
        }
    }


    @Test
    fun `loading water companies`() {
        val text = """[
  {
    "name": "Anglian Water",
    "address": {
      "line1": "Lancaster House",
      "line2": "Lancaster Way",
      "line3": "Ermine Business Park",
      "town": "Huntindon",
      "postcode": "PE29 6YJ"
    },
    "phone": "+44 1480 323 000",
    "web": "http://www.anglianwater.co.uk/",
    "twitter": "@AnglianWater"
  }]"""

        val companies = WaterCompanies(
            routes("water-companies.json" bind { _: Request -> Response(Status.OK).body(text) })
        )

        expectThat(companies()).hasSize(1)
    }

    @Test
    fun `loading constituency rankings`() {
        val text = """[
  {
    "constituency": "PP",
    "company": "DC",
    "total_spills": 6754.0,
    "total_hours": 79501.0,
    "spills_increase": 178.0,
    "hours_increase": 6910.0,
    "mp_name": "SC",
    "mp_party": "C",
    "mp_uri": "http://example.com/sc",
    "twitter_handle": "@example"
  }]"""

        val rankings = ConstituencyRankings(
            routes("spills-by-constituency.json" bind { _: Request -> Response(Status.OK).body(text) })
        )

        expectThat(rankings()).hasSize(1).and {
            get(0).and {
                get { rank }.isEqualTo(1)
                get { constituencyName }.isEqualTo(ConstituencyName("PP"))
                get { company }.isEqualTo("DC")
                get { mp }.and {
                    get { name }.isEqualTo("SC")
                    get { party }.isEqualTo("C")
                    get { handle }.isEqualTo("@example")
                    get { uri }.isEqualTo(Uri.of("http://example.com/sc"))
                }
                get { count }.isEqualTo(6754)
                get { duration }.isEqualTo(Duration.ofHours(79501))
                get { countDelta }.isEqualTo(178)
                get { durationDelta }.isEqualTo(Duration.ofHours(6910))
            }
        }

    }
}