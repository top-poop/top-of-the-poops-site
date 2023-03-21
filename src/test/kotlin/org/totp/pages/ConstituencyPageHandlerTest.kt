package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.strikt.header
import org.http4k.strikt.status
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.size
import java.nio.file.Path
import java.time.Duration

class ConstituencyPageHandlerTest {

    val summaries = listOf(CSOTotals(
        constituency = ConstituencyName("Your House"),
        cso=CSO(company = "Venture Cap",
            sitename = "Your House",
            waterway = "Rour River",
            location= Coordinates(lon=-0.12460789, lat=51.49993385),
        ),
        count=100,
        duration = Duration.ofHours(100),
        reporting= 0.0f
    ))

    val service = routes(
        "/{constituency}" bind Method.GET to ConstituencyPageHandler { summaries }
    )

    @Test
    fun `renders a constituency`() {
        expectThat(service(Request(Method.GET, "/aldershot"))).status.isEqualTo(Status.OK)
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
    fun `loading cso summaries`() {
        val summaries = csoSummaries(Path.of("/home/richja/dev/gis/web/data/generated/spills-all.json"))
        expectThat(summaries(ConstituencyName("Aldershot"))) {
            size.isEqualTo(2)
        }
    }
}