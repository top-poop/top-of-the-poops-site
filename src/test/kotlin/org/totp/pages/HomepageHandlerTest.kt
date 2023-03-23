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
import org.totp.model.data.ConstituencyName
import org.totp.model.data.ConstituencyRankings
import strikt.api.expectThat
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import strikt.assertions.size
import java.time.Duration

class HomepageHandlerTest {

    val service = routes(
        "/" bind Method.GET to HomepageHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            rankings = {
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
            }
        )
    )

    @Test
    fun `renders the homepage`() {
        val html = Html(service(Request(Method.GET, "/")))

        expectThat(html).select("table[data-id='constituency']").hasSize(1)
        expectThat(html).select("table[data-id='constituency'] tbody tr").and {
            size.isGreaterThan(2)
        }
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