package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.strikt.header
import org.http4k.strikt.status
import org.junit.jupiter.api.Test
import org.totp.model.data.CSO
import org.totp.model.data.CSOTotals
import org.totp.model.data.ConstituencyBoundaries
import org.totp.model.data.ConstituencyCSOs
import org.totp.model.data.ConstituencyName
import org.totp.model.data.Coordinates
import org.totp.model.data.GeoJSON
import strikt.api.expectThat
import strikt.assertions.get
import strikt.assertions.isEqualTo
import strikt.assertions.size
import java.time.Duration

class ConstituencyPageHandlerTest {

    val summaries = listOf(
        CSOTotals(
            constituency = ConstituencyName("Your House"),
            cso = CSO(
                company = "Venture Cap",
                sitename = "Your House",
                waterway = "Your River",
                location = Coordinates(lon = -0.12460789, lat = 51.49993385),
            ),
            count = 100,
            duration = Duration.ofHours(100),
            reporting = 0.0f
        )
    )

    val service = routes(
        "/{constituency}" bind Method.GET to ConstituencyPageHandler(
            constituencySpills = { summaries },
            constituencyBoundary = { GeoJSON.of("some geojson") }
        )
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

        val text = """[
  {
    "constituency": "Aberavon",
    "company_name": "Dwr Cymru Welsh Water",
    "site_name": "BAGLAN SPS  BRITON FERRY  NEATH",
    "receiving_water": "Swansea Bay",
    "lat": 51.576878,
    "lon": -3.873983,
    "spill_count": 173.0,
    "total_spill_hours": 1651.0,
    "reporting_percent": 100.0
  }]"""

        val summaries = ConstituencyCSOs { _ -> Response(Status.OK).body(text) }
        expectThat(summaries(ConstituencyName("Aberavon"))) {
            size.isEqualTo(1)
            get(0).and {
                get { constituency }.isEqualTo(ConstituencyName("Aberavon"))
                get { cso }.and {
                    get { location }.isEqualTo(Coordinates(lat = 51.576878, lon = -3.873983))
                }
                get { count }.isEqualTo(173)
                get { duration }.isEqualTo(Duration.ofHours(1651))
            }
        }
    }

    @Test
    fun `loading constituency boundaries`() {
        val handler = routes("/aberavon.json" bind { _: Request -> Response(Status.OK).body("hi") })
        val boundaries = ConstituencyBoundaries(handler)
        expectThat(boundaries(ConstituencyName("Aberavon"))).isEqualTo(GeoJSON("hi"))
    }
}