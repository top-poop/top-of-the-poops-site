package org.totp.pages

import org.http4k.core.*
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.db.StreamData
import org.totp.model.TotpHandlebars
import org.totp.model.data.*
import strikt.api.expectThat
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import java.time.Duration
import java.time.LocalDate

val aWaterCompany = WaterCompany(
    CompanyName.of("Water Co"),
    Address("1", "2", "3", "town", "postcode"),
    Uri.of(""),
    Uri.of("http://example.com/company"),
    Uri.of("http://example.com/image"),
    linkUri = Uri.of("http://example.com/link"),
    "@bob"
)


class HomepageHandlerTest {

    val service = routes(
        "/" bind Method.GET to HomepageHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            constituencyRankings = {
                listOf(
                    ConstituencyRank(
                        1,
                        ConstituencyName("a"),
                        100,
                        Duration.ofHours(1),
                        25,
                        Duration.ofHours(10)
                    ),
                    ConstituencyRank(
                        2,
                        ConstituencyName("b"),
                        2,
                        Duration.ofHours(2),
                        50,
                        Duration.ofHours(20)
                    )
                )
            },
            bathingRankings = {
                listOf(
                    BathingRank(
                        1,
                        BathingName.of("beach"),
                        CompanyName.of("company"),
                        10,
                        Duration.ofHours(1),
                        DeltaValue.of(10),
                        Duration.ofSeconds(11),
                        loc = Coordinates(1.0, 1.0),
                        geo = GeoJSON(""),
                    )
                )
            },
            shellfishRankings = {
                listOf(
                    ShellfishRank(
                        1,
                        ShellfisheryName.of("shellfish"),
                        CompanyName.of("company"),
                        10,
                        Duration.ofHours(1),
                        DeltaValue.of(10),
                        Duration.ofSeconds(11)
                    )
                )
            },
            riverRankings = {
                listOf(
                    RiverRank(
                        1,
                        WaterwayName("river"),
                        CompanyName("company"),
                        20,
                        Duration.ofHours(22),
                        DeltaValue.of(10),
                        Duration.ofHours(10),
                        bbox = BoundingBox(Coordinates(1.0, 1.0), Coordinates(1.0, 1.0)),
                        geo = GeoJSON("")
                    )
                )
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
            companies = { listOf(aWaterCompany) },
            mpFor = { MP("mp1", "con", "handle1", Uri.of("https://example.com/1")) },
            streamSummary = {
                StreamData.StreamOverflowSummary(
                    StreamData.StreamCSOCount(
                        start = 123,
                        stop = 45,
                        offline = 50
                    ), emptyList()
                )
            }
        )
    )

    @Test
    fun `renders the homepage`() {
        val html = Html(service(Request(Method.GET, "/")))

        expectThat(html).select(".footer").isNotEmpty()

        expectThat(html).twitterImageUri()
            .isEqualTo("https://top-of-the-poops.org/badges/home/home-2024.png")
    }


    @Test
    fun `loading media appearances`() {

        val text = """[
  {
    "href": "http://example.com",
    "where": "Some August Publication",
    "title": "Nice Title",
    "date": "2024-01-01",
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
                get { date }.isEqualTo(LocalDate.of(2024, 1, 1))
                get { imageUri }.isEqualTo(Uri.of("http://example.com/image.jpg"))
            }
        }
    }
}