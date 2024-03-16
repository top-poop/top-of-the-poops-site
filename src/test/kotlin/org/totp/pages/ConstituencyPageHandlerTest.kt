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
import org.totp.LastModified
import org.totp.model.TotpHandlebars
import org.totp.model.data.CSO
import org.totp.model.data.CSOTotals
import org.totp.model.data.CompanyName
import org.totp.model.data.ConstituencyLiveData
import org.totp.model.data.ConstituencyName
import org.totp.model.data.Coordinates
import org.totp.model.data.GeoJSON
import org.totp.model.data.WaterwayName
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import java.time.Duration
import java.time.Instant

val anMP = MP("bob", "con", null, Uri.of("http://example.com"))


class ConstituencyPageHandlerTest {

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
        "/{constituency}" bind Method.GET to ConstituencyPageHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            constituencySpills = { summaries },
            constituencyBoundary = { GeoJSON.of("some geojson") },
            constituencyLiveData = { ConstituencyLiveData(ConstituencyName.of("a"), 1, 2) },
            mpFor = {
                anMP
            },
            constituencyLiveAvailable = { listOf(ConstituencyName("bob")) },
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
        )
    )

    @Test
    fun `renders a constituency`() {
        val html = Html(service(Request(Method.GET, "/aldershot").header("host", "bob.com")))

        expectThat(html).twitterImageUri()
            .isEqualTo("https://top-of-the-poops.org/badges/constituency/aldershot.png")

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