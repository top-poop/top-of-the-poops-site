package org.totp.pages

import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.data.AllSpills
import org.totp.model.data.CompanyAnnualSummaries
import org.totp.model.data.ConstituencyBoundaries
import org.totp.model.data.ConstituencyContacts
import org.totp.model.data.ConstituencyLiveAvailability
import org.totp.model.data.ConstituencyLiveDataLoader
import org.totp.model.data.ConstituencyName
import org.totp.model.data.ConstituencyRankings
import org.totp.model.data.Coordinates
import org.totp.model.data.GeoJSON
import org.totp.model.data.RiverRankings
import org.totp.model.data.WaterCompanies
import strikt.api.expectThat
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import strikt.assertions.isNotNull
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
        val boundaries = ConstituencyBoundaries(handler)
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


    @Test
    fun `loading constituency live data`() {
        val text = """{
  "cso": [
    {
      "p": "Sandhurst",
      "cid": "TEMP.2881",
      "d": "2022-12-01",
      "a": "u-24"
    },
    {
      "p": "ALDERSHOT STW",
      "cid": "CTCR.1974",
      "d": "2022-12-01",
      "a": "u-24"
    }]}"""

        val livedata = ConstituencyLiveDataLoader(
            routes("/live/constituencies/aldershot.json" bind { _: Request -> Response(Status.OK).body(text) })
        )

        val name = ConstituencyName.of("Aldershot")

        expectThat(livedata(name)).isNotNull().and {
            get { constituencyName }.isEqualTo(name)
            get { dates }.isEqualTo(1)
            get { csos }.isEqualTo(2)
        }
    }

    @Test
    fun `loading actual live data`() {
        val content = File("services/data/datafiles/live/constituencies/aldershot.json")
        val service = ConstituencyLiveDataLoader { Response(Status.OK).body(content.readText()) }
        service(ConstituencyName.of("bob"))
    }

    @Test
    fun `loading available live data names`() {
        val content = File("services/data/datafiles/live/constituencies/constituencies-available.json")
        val service = ConstituencyLiveAvailability { Response(Status.OK).body(content.readText()) }
        expectThat(service()).size.isGreaterThan(3)
    }

    @Test
    fun `loading river rankings`() {
        val content = File("services/data/datafiles/v1/2021/spills-by-river.json")
        val service = RiverRankings { Response(Status.OK).body(content.readText()) }
        expectThat(service()).size.isGreaterThan(3)
    }

    @Test
    fun `loading company summaries`() {
        val content = File("services/data/datafiles/v1/2021/spills-by-company.json")
        val service = CompanyAnnualSummaries { Response(Status.OK).body(content.readText()) }
        expectThat(service()).size.isGreaterThan(3)
    }

    @Test
    fun `loading constituency socials`() {
        val content = File("services/data/datafiles/v1/2021/constituency-social.json")
        val service = ConstituencyContacts { Response(Status.OK).body(content.readText()) }
        expectThat(service()).size.isGreaterThan(3)
    }


    @Test
    fun `loading river rankings 2022`() {
        val content = File("services/data/datafiles/v1/2022/spills-by-river.json")
        val service = RiverRankings { Response(Status.OK).body(content.readText()) }
        expectThat(service()).size.isGreaterThan(3)
    }

    @Test
    fun `loading company summaries 2022`() {
        val content = File("services/data/datafiles/v1/2022/spills-by-company.json")
        val service = CompanyAnnualSummaries { Response(Status.OK).body(content.readText()) }
        expectThat(service()).size.isGreaterThan(3)
    }

    @Test
    fun `loading constituency socials 2022`() {
        val content = File("services/data/datafiles/v1/2022/constituency-social.json")
        val service = ConstituencyContacts { Response(Status.OK).body(content.readText()) }
        expectThat(service()).size.isGreaterThan(3)
    }
}