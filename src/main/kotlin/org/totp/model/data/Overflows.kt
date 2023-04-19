package org.totp.model.data

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.format.Jackson.asA
import org.http4k.format.value
import org.totp.extensions.kebabCase
import org.totp.extensions.readSimpleList
import org.totp.pages.CompanyAnnualSummary
import org.totp.pages.ConstituencyRank
import org.totp.pages.DeltaValue
import org.totp.pages.EnsureSuccessfulResponse
import org.totp.pages.MP
import org.totp.pages.WaterwaySlug
import java.time.Duration
import java.time.LocalDate

data class CSOTotals(
    val constituency: ConstituencyName,
    val cso: CSO,
    val count: Int,
    val duration: Duration,
    val reporting: Number,
)

data class CSO(
    val company: CompanyName,
    val sitename: String,
    val waterway: WaterwayName,
    val location: Coordinates,
)

data class LiveDataCSO(
    @JsonProperty("p") val site: String,
    @JsonProperty("cid") val permit: String,
    @JsonProperty("d") val date: LocalDate,
    @JsonProperty("a") val category: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LiveData(val cso: List<LiveDataCSO>)

fun fromEDMHours(hours: Double): Duration {
    val seconds = hours * 3600
    return Duration.ofSeconds(seconds.toLong())
}


object ConstituencyLiveAvailability {
    operator fun invoke(handler: HttpHandler): () -> List<ConstituencyName> {
        return {
            val uri = Uri.of("/live/constituencies/constituencies-available.json")
            val response = handler(Request(Method.GET, uri))

            TotpJson.mapper.readValue(response.bodyString())
        }
    }
}


data class ConstituencyLiveData(val constituencyName: ConstituencyName, val dates: Int, val csos: Int)

object ConstituencyLiveDataLoader {
    operator fun invoke(handler: HttpHandler): (ConstituencyName) -> ConstituencyLiveData? {
        return { name ->
            val uri = ConstituencySlug.from(name).let { Uri.of("/live/constituencies/$it.json") }
            val response = handler(Request(Method.GET, uri))

            if (response.status.successful) {
                val value = response.bodyString().asA(LiveData::class)
                value.let {
                    ConstituencyLiveData(
                        name,
                        csos = it.cso.map { it.site }.toSet().size,
                        dates = it.cso.map { it.date }.toSet().size
                    )
                }
            } else {
                null
            }
        }
    }
}

object ConstituencyBoundaries {
    operator fun invoke(handler: HttpHandler): (ConstituencyName) -> GeoJSON {
        val handler = EnsureSuccessfulResponse().then(handler)
        return { name ->
            val slug = ConstituencySlug.from(name)
            val uri = Uri.of("$slug.json")
            GeoJSON(handler(Request(Method.GET, uri)).bodyString())
        }
    }
}

object BeachBoundaries {
    operator fun invoke(handler: HttpHandler): (BeachName) -> GeoJSON? {
        return { name ->
            val slug = name.value.kebabCase()
            val uri = Uri.of("$slug.json")
            val response = handler(Request(Method.GET, uri))
            if (response.status.successful) {
                GeoJSON(response.bodyString())
            } else {
                null
            }
        }
    }
}

object ConstituencyRankings {
    operator fun invoke(handler: HttpHandler): () -> List<ConstituencyRank> {
        return {
            val response = handler(Request(Method.GET, "spills-by-constituency.json"))

            TotpJson.mapper.readSimpleList(response.bodyString())
                .mapIndexed { r, it ->
                    val constituencyName = ConstituencyName(it["constituency"] as String)
                    ConstituencyRank(
                        rank = r + 1,
                        constituencyName = constituencyName,
                        count = (it["total_spills"] as Double).toInt(),
                        duration = fromEDMHours((it["total_hours"] as Double)),
                        countDelta = (it["spills_increase"] as Double).toInt(),
                        durationDelta = fromEDMHours(it["hours_increase"] as Double)
                    )
                }
        }
    }
}


data class BathingRank(
    val rank: Int,
    val beach: BathingName,
    val company: CompanyName,
    val count: Int,
    val duration: Duration,
    val countDelta: DeltaValue,
    val durationDelta: Duration,
)

object BathingRankings {
    operator fun invoke(handler: HttpHandler): () -> List<BathingRank> {
        return {
            val response = handler(Request(Method.GET, "spills-by-beach.json"))

            TotpJson.mapper.readSimpleList(response.bodyString())
                .mapIndexed { r, it ->
                    BathingRank(
                        rank = r + 1,
                        beach = BathingName(it["bathing"] as String),
                        company = CompanyName(it["company_name"] as String),
                        duration = fromEDMHours(it["total_spill_hours"] as Double),
                        count = (it["total_spill_count"] as Double).toInt(),
                        countDelta = DeltaValue.of((it["spills_increase"] as Double).toInt()),
                        durationDelta = fromEDMHours(it["hours_increase"] as Double)
                    )
                }
        }
    }
}


data class RiverRank(
    val rank: Int,
    val river: WaterwayName,
    val company: CompanyName,
    val count: Int,
    val duration: Duration,
    val countDelta: DeltaValue,
    val durationDelta: Duration,
)

data class ShellfishRank(
    val rank: Int,
    val shellfishery: ShellfisheryName,
    val company: CompanyName,
    val count: Int,
    val duration: Duration,
    val countDelta: DeltaValue,
    val durationDelta: Duration,
)

object RiverRankings {
    operator fun invoke(handler: HttpHandler): () -> List<RiverRank> {
        return {
            val response = handler(Request(Method.GET, "spills-by-river.json"))

            TotpJson.mapper.readSimpleList(response.bodyString())
                .mapIndexed { r, it ->
                    RiverRank(
                        rank = r + 1,
                        river = WaterwayName.of(it["river_name"] as String),
                        company = CompanyName.of(it["company_name"] as String),
                        duration = fromEDMHours(it["total_hours"] as Double),
                        count = (it["total_count"] as Double).toInt(),
                        countDelta = DeltaValue.of((it["spills_increase"] as Double).toInt()),
                        durationDelta = fromEDMHours(it["hours_increase"] as Double)
                    )
                }
        }
    }
}


object AllSpills {
    operator fun invoke(handler: HttpHandler): () -> List<CSOTotals> {
        return {
            val response = handler(Request(Method.GET, "spills-all.json"))

            TotpJson.mapper.readSimpleList(response.bodyString())
                .map {
                    CSOTotals(
                        constituency = ConstituencyName(it["constituency"] as String),
                        cso = CSO(
                            company = CompanyName.of(it["company_name"] as String),
                            sitename = it["site_name"] as String,
                            waterway = WaterwayName.of(it["receiving_water"] as String),
                            location = Coordinates(
                                lat = it["lat"] as Double,
                                lon = it["lon"] as Double
                            )
                        ),
                        count = (it["spill_count"] as Double).toInt(),
                        duration = fromEDMHours(it["total_spill_hours"] as Double),
                        reporting = it["reporting_percent"] as Double
                    )
                }
        }
    }
}


fun constituencyCSOs(source: () -> List<CSOTotals>) =
    { name: ConstituencyName ->
        source().filter { name == it.constituency }
    }

fun waterwayCSOs(source: () -> List<CSOTotals>) =
    { name: WaterwaySlug, company: CompanySlug ->
        val result = source()
            .filter { name == WaterwaySlug.from(it.cso.waterway) }
            .filter { company == it.cso.company.toSlug() }
        result
    }

fun constituencyRivers(csos: () -> List<CSOTotals>, rivers: () -> List<RiverRank>) = { name: ConstituencyName ->
    val waterways = csos()
        .asSequence()
        .filter { it.constituency == name }
        .map { it.cso.company to it.cso.waterway }
        .toSet()

    rivers()
        .filter { it.company to it.river in waterways }
        .sortedBy { it.rank }
}


data class MediaAppearance(
    val title: String,
    val publication: String,
    val date: LocalDate,
    val uri: Uri,
    val imageUri: Uri,
)

object MediaAppearances {
    operator fun invoke(handler: HttpHandler): () -> List<MediaAppearance> {
        val handler = EnsureSuccessfulResponse().then(handler)
        return {
            val response = handler(Request(Method.GET, "media-appearances.json"))

            TotpJson.mapper.readSimpleList(response.bodyString())
                .map {
                    MediaAppearance(
                        title = it["title"] as String,
                        publication = it["where"] as String,
                        date = LocalDate.parse(it["date"] as String),
                        uri = Uri.of(it["href"] as String),
                        imageUri = Uri.of(it["image"] as String)
                    )
                }
        }
    }
}


data class Address(val line1: String, val line2: String, val line3: String?, val town: String, val postcode: String)
data class WaterCompany(
    val name: CompanyName,
    val address: Address,
    val phone: Uri,
    val uri: Uri,
    val imageUri: Uri,
    val linkUri: Uri,
    val handle: String?,
)

object WaterCompanies {
    operator fun invoke(handler: HttpHandler): () -> List<WaterCompany> {
        return {
            val response = handler(Request(Method.GET, "water-companies.json"))

            TotpJson.mapper.readSimpleList(response.bodyString())
                .map {
                    val name = CompanyName.of(it["name"] as String)
                    val slug = name.toSlug()
                    WaterCompany(
                        name = name,
                        (it["address"] as Map<String, String?>).let {
                            Address(
                                line1 = it["line1"] as String,
                                line2 = it["line2"] as String,
                                line3 = it["line3"],
                                town = it["town"] as String,
                                postcode = it["postcode"] as String
                            )
                        },
                        phone = Uri.of(""),
                        uri = Uri.of(it["web"] as String),
                        imageUri = Uri.of("/assets/images/logos/$slug.png"),
                        handle = it["twitter"] as String?,
                        linkUri = Uri.of("/company/$slug")
                    )
                }
        }
    }
}


object CompanyAnnualSummaries {
    operator fun invoke(handler: HttpHandler): () -> List<CompanyAnnualSummary> {
        return {
            val response = handler(Request(Method.GET, "spills-by-company.json"))

            TotpJson.mapper.readSimpleList(response.bodyString())
                .map {
                    CompanyAnnualSummary(
                        CompanyName.of(it["company_name"] as String),
                        it["reporting_year"] as Int,
                        (it["count"] as Double).toInt(),
                        fromEDMHours(it["hours"] as Double),
                        it["location_count"] as Int
                    )
                }
        }
    }
}

data class ConstituencyContact(
    val constituency: ConstituencyName,
    val mp: MP,
)

object ConstituencyContacts {
    operator fun invoke(handler: HttpHandler): () -> List<ConstituencyContact> {
        return {
            val response = handler(Request(Method.GET, "constituency-social.json"))

            TotpJson.mapper.readSimpleList(response.bodyString())
                .map {
                    ConstituencyContact(
                        ConstituencyName.of(it["constituency"] as String),
                        mp = MP(
                            name = it["mp_name"] as String,
                            party = it["mp_party"] as String,
                            handle = it["twitter_handle"] as String?,
                            uri = Uri.of(it["mp_uri"] as String)
                        ),
                    )
                }
        }
    }
}

object ConstituencyNeighbours {
    operator fun invoke(handler: HttpHandler): (ConstituencyName) -> List<ConstituencyName> {
        return { wanted ->
            val response = handler(Request(Method.GET, "constituency-neighbours.json"))

            TotpJson.mapper.readSimpleList(response.bodyString())
                .map {
                    ConstituencyName(it["pcon20nm"] as String) to
                            ConstituencyName(it["neighbour"] as String)
                }
                .filter {
                    it.first == wanted
                }
                .map {
                    it.second
                }
        }
    }
}

data class BathingCSO(
    val year: Int,
    val company: CompanyName,
    val sitename: String,
    val bathing: BathingName,
    val count: Int,
    val duration: Duration,
    val reporting: Number,
    val waterway: WaterwayName,
    val location: Coordinates,
    val constituency: ConstituencyName,
    val beach: BeachName?,
)


object BathingCSOs {
    operator fun invoke(handler: HttpHandler): () -> List<BathingCSO> {
        val response = handler(Request(Method.GET, "csos-by-beach.json"))

        return {
            TotpJson.mapper.readSimpleList(response.bodyString())
                .map {
                    BathingCSO(
                        year = it["reporting_year"] as Int,
                        company = CompanyName(it["company_name"] as String),
                        sitename = it["site_name"] as String,
                        bathing = BathingName(it["bathing"] as String),
                        count = (it["spill_count"] as Double).toInt(),
                        duration = fromEDMHours(it["total_spill_hours"] as Double),
                        reporting = it["reporting_pct"] as Double,
                        waterway = WaterwayName(it["receiving_water"] as String),
                        location = Coordinates(it["lat"] as Double, it["lon"] as Double),
                        constituency = ConstituencyName(it["pcon20nm"] as String),
                        beach = (it["beach_name"] as String?)?.let { BeachName(it) }
                    )
                }
        }
    }
}
