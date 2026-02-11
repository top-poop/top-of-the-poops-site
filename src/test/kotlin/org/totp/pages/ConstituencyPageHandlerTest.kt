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
import org.totp.db.StreamData
import org.totp.model.TotpHandlebars
import org.totp.model.data.*
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import java.time.Clock
import java.time.Duration

val anMP = MP("bob", "con", null, Uri.of("http://example.com"))


class ConstituencyPageHandlerTest {

    val clock = Clock.systemUTC()

    var summaries = listOf(
        CSOTotals(
            constituency = ConstituencyName("Your House"),
            places = listOf(PlaceName.of("v")),
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
        "/{constituency}" bind Method.GET to ConstituencyPageHandler(
            clock = clock,
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            constituencySpills = { summaries },
            constituencyBoundary = { GeoJSON.of("some geojson") },
            mpFor = {
                anMP
            },
            constituencyLiveAvailable = { setOf(ConstituencyName("bob")) },
            constituencyNeighbours = { listOf(ConstituencyName("c"), ConstituencyName("d")) },
            constituencyRank = {
                ConstituencyRank(
                    1,
                    ConstituencyName("Aldershot"),
                    10,
                    Duration.ofHours(1),
                    20,
                    Duration.ZERO
                )
            },
            constituencyRivers = { listOf(aRiver(1)) },
            constituencyLiveTotals = { c, _, _ ->
                StreamData.ConstituencyLiveTotal(
                    constituency = c,
                    duration = Duration.ofHours(10),
                    csoCount = 10
                )
            },
            liveDataLatest = clock::instant,
        )
    )

    @Test
    fun `renders a constituency`() {
        val html = Html(service(Request(Method.GET, "/aldershot").header("host", "bob.com")))

        expectThat(html).twitterImageUri()
            .isEqualTo("https://top-of-the-poops.org/badges/constituency/aldershot-2024.png")

        expectThat(html)
            .select("link[rel='canonical']").first()
            .attribute("href")
            .isEqualTo("http://bob.com/aldershot")
    }

    @Test

    fun `renders a constituency with accents`() {
        Html(service(Request(Method.GET, "/ynys-mon")))
    }

    @Test
    fun `renders a constituency with no overflows`() {
        // surprisingly there are one or two
        summaries = listOf()
        Html(service(Request(Method.GET, "/aldershot")))
    }

    @Test
    fun `redirects non-kebab to kebab`() {
        expectThat(service(Request(Method.GET, "/Aldershot"))) {
            status.isEqualTo(Status.TEMPORARY_REDIRECT)
            header("location").isEqualTo("/aldershot")
        }

        expectThat(service(Request(Method.GET, "/Islington South and Finsbury"))) {
            status.isEqualTo(Status.TEMPORARY_REDIRECT)
            header("location").isEqualTo("/islington-south-and-finsbury")
        }
    }

    @Test
    fun `handlebars functions`() {
        val handlebars = TotpHandlebars.handlebars()

        expectThat(
            handlebars
                .compile(StringTemplateSource("blerg", "{{concat 'a' 'b' 'c'}}"))
                .apply(Object())
        )
            .isEqualTo("abc")

        expectThat(
            handlebars
                .compile(StringTemplateSource("blerg", "{{urlencode value}}"))
                .apply(mapOf("value" to Uri.of("https://host?param1=value1&param2")))
        )
            .isEqualTo("https%3A%2F%2Fhost%3Fparam1%3Dvalue1%26param2")

        expectCatching {
            handlebars
                .compile(StringTemplateSource("blerg", "{{ invalid }}"))
                .apply(Object())
        }.isFailure()

    }
}