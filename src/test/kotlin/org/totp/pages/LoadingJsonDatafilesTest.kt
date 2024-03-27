package org.totp.pages

import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.data.AllSpills
import org.totp.model.data.BathingCSOs
import org.totp.model.data.BathingRankings
import org.totp.model.data.Boundaries
import org.totp.model.data.CompanyAnnualSummaries
import org.totp.model.data.ConstituencyBoundaries
import org.totp.model.data.ConstituencyName
import org.totp.model.data.ConstituencyNeighbours
import org.totp.model.data.ConstituencyRankings
import org.totp.model.data.Coordinates
import org.totp.model.data.GeoJSON
import org.totp.model.data.RiverRankings
import org.totp.model.data.ShellfishCSOs
import org.totp.model.data.ShellfishRankings
import org.totp.model.data.WaterCompanies
import strikt.api.expectThat
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import strikt.assertions.isNotBlank
import strikt.assertions.size
import java.io.File
import java.time.Duration

class LoadingJsonDatafilesTest {


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

        val summaries =
            AllSpills(
                routes("/spills-all.json" bind { _: Request -> Response(Status.OK).body(text) })
            )

        expectThat(summaries()) {
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
        val boundaries = ConstituencyBoundaries(Boundaries(handler))
        expectThat(boundaries(ConstituencyName("Aberavon"))).isEqualTo(GeoJSON("hi"))
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
                get { count }.isEqualTo(6754)
                get { duration }.isEqualTo(Duration.ofHours(79501))
                get { countDelta }.isEqualTo(178)
                get { durationDelta }.isEqualTo(Duration.ofHours(6910))
            }
        }
    }



    fun uriHavingFileContent(uri: Uri, file: File): RoutingHttpHandler {
        expectThat(uri.path).isNotBlank()
        if ( ! file.exists() ) {
            throw IllegalArgumentException("given files does not exist  $file" )
        }
        val handler: HttpHandler = { Response(Status.OK).body(file.readText()) }
        return EnsureSuccessfulResponse().then(routes(
            uri.path bind Method.GET to handler,
        ))
    }

    @Test
    fun `loading river rankings 2023`() {
        val remote = uriHavingFileContent(
            Uri.of("spills-by-river.json"),
            File("services/data/datafiles/v1/2023/spills-by-river.json")
        )
        val service = RiverRankings(remote)
        expectThat(service()).size.isGreaterThan(3)
    }

    @Test
    fun `loading company summaries 2023`() {
        val remote = uriHavingFileContent(
            Uri.of("spills-by-company.json"),
            File("services/data/datafiles/v1/2023/spills-by-company.json")
        )
        val service = CompanyAnnualSummaries(remote)
        expectThat(service()).size.isGreaterThan(3)
    }

    @Test
    fun `loading  beaches 2023`() {
        val remote = uriHavingFileContent(
            Uri.of("spills-by-beach.json"),
            File("services/data/datafiles/v1/2023/spills-by-beach.json")
        )
        val service = BathingRankings(remote)
        expectThat(service()).size.isGreaterThan(3)
    }

    @Test
    fun `loading  shellfish 2023`() {
        val remote = uriHavingFileContent(
            Uri.of("spills-by-shellfish.json"),
            File("services/data/datafiles/v1/2023/spills-by-shellfish.json")
        )
        val service = ShellfishRankings(remote)
        expectThat(service()).size.isGreaterThan(3)
    }

    @Test
    fun `loading  bathing csos 2023`() {
        val remote = uriHavingFileContent(
            Uri.of("csos-by-beach.json"),
            File("services/data/datafiles/v1/2023/csos-by-beach.json")
        )
        val service = BathingCSOs(remote)
        expectThat(service()).size.isGreaterThan(3)
    }

    @Test
    fun `loading  shellfish csos 2023`() {
        val remote = uriHavingFileContent(
            Uri.of("csos-by-shellfish.json"),
            File("services/data/datafiles/v1/2023/csos-by-shellfish.json")
        )
        val service = ShellfishCSOs(remote)
        expectThat(service()).size.isGreaterThan(3)
    }

    @Test
    fun `loading  neighbours 2023`() {

        val remote = uriHavingFileContent(
            Uri.of("constituency-neighbours.json"),
            File("services/data/datafiles/v1/2023/constituency-neighbours.json")
        )

        val service = ConstituencyNeighbours(remote)
        expectThat(service(ConstituencyName("Aldershot"))).size.isEqualTo(4)
    }
}