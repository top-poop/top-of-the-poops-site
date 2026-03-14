package org.totp.search

import org.http4k.testing.RecordingEvents
import org.junit.jupiter.api.Test
import org.totp.db.testDbConnection
import org.totp.model.data.*
import org.totp.pages.DeltaValue
import strikt.api.expectThat
import strikt.assertions.*
import java.time.Duration
import java.util.concurrent.Executors

class SearcherTest {

    val events = RecordingEvents()

    val executor = Executors.newFixedThreadPool(5)

    val searcher = Searcher(events, testDbConnection, executor, {
        listOf(
            RiverRank(
                1, river = WaterwayName.of("River Thames"),
                company = Companies.Thames.companyName,
                count = 124,
                duration = Duration.ofSeconds(123),
                countDelta = DeltaValue.of(124),
                durationDelta = Duration.ofSeconds(23),
                bbox = BoundingBox(ne = Coordinates(1.0, 2.0), sw = Coordinates(3.0, 4.0)),
                geo = GeoJSON.of("dsfsdf")
            )
        )
    })

    @Test
    fun `no results if query is empty`() {
        expectThat(searcher.search("")).size.isEqualTo(0)
        expectThat(searcher.search("   ")).size.isEqualTo(0)
    }

    @Test
    fun `finds constituencies by english name any case`() {
        val results = searcher.search("hampshire")
        expectThat(results).isNotEmpty()
        expectThat(results.map { it.text.lowercase() }).all { contains("hampshire") }
    }

    @Test
    fun `finds constituencies by welsh name any case`() {
        val results = searcher.search("gŵyr").filter { it.type == SearchResultType.Constituency }
        expectThat(results) {
            size.isEqualTo(1)
        }
        expectThat(results).first().and {
            get { text }.contains("Gower").contains("Gŵyr")
        }
    }

    @Test
    fun `finds constituencies by welsh name regular characters`() {
        val results = searcher.search("mon")
        expectThat(results).filter { it.text.equals("Ynys Môn") }.isNotEmpty()
    }

    @Test
    fun `does not find constituencies in NI cos we don't have them yet`() {
        val results = searcher.search("belfast")
        expectThat(results).isEmpty()
    }

    @Test
    fun `finds places`() {
        val results = searcher.search("basingstoke")
        expectThat(results).filter { it.type == SearchResultType.Place }.isNotEmpty()
    }

    @Test
    fun `finds overflows by id`() {
        val results = searcher.search("AWS00095")
        expectThat(results).isNotEmpty()
    }

    @Test
    fun `finds water company`() {
        val results = searcher.search("thames").filter { it.type == SearchResultType.WaterCompany }
        expectThat(results).first().and {
            get { this.text }.isEqualTo("Thames Water")
        }
    }

    @Test
    fun `finds river`() {
        val results = searcher.search("river thames").filter { it.type == SearchResultType.River }
        expectThat(results).first().and {
            get { this.text }.isEqualTo("River Thames / Thames Water")
        }
    }

    @Test
    fun `finds postcode place`() {
        val results = searcher.search("PE28").filter { it.type == SearchResultType.Place }
        expectThat(results).size.isGreaterThan(20)
    }

    @Test
    fun `finds postcode lowercase`() {
        expectThat(searcher.search("PE28")).isEqualTo(searcher.search("pe28"))
    }

    @Test
    fun `finds postcode place and can narrow`() {
        val results = searcher.search("PE28 u").filter { it.type == SearchResultType.Place }
        expectThat(results).first().and {
            get { this.text }.isEqualTo("Huntingdon")
        }
    }

    @Test
    fun `finds postcode constituency`() {
        val results = searcher.search("PE28").filter { it.type == SearchResultType.Constituency }
        expectThat(results).and {
            size.isGreaterThan(3)
        }
    }

    @Test
    fun `finds postcode constituency and can narrow`() {
        val results = searcher.search("PE28 w").filter { it.type == SearchResultType.Constituency }
        expectThat(results).first().and {
            get { this.text }.isEqualTo("North West Cambridgeshire")
        }
    }

    @Test
    fun `finds overflows by site name`() {
        val results = searcher.search("London")
        expectThat(results)
            .filter { it.name == "AnW1547" }
            .isNotEmpty()
            .first().and {
                get { this.text }.isEqualTo("AnW1547 / Anglian Water / WYMONDHAM-LONDON RD SP / River Tiffey")
            }
    }
}