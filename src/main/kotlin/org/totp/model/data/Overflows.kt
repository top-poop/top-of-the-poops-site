package org.totp.model.data

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.http4k.core.*
import org.totp.extensions.kebabCase
import org.totp.extensions.readSimpleList
import org.totp.pages.*
import java.io.IOException
import java.time.Duration
import java.time.LocalDate

data class CSOTotals(
    val constituency: ConstituencyName,
    val places: List<PlaceName>,
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
    @param:JsonProperty("p") val site: String,
    @param:JsonProperty("cid") val permit: String,
    @param:JsonProperty("d") val date: LocalDate,
    @param:JsonProperty("a") val category: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LiveData(val cso: List<LiveDataCSO>)

fun fromEDMHours(hours: Double): Duration {
    val seconds = hours * 3600
    return Duration.ofSeconds(seconds.toLong())
}


object ConstituencyBoundaries {
    operator fun invoke(handler: (String) -> GeoJSON?): (ConstituencyName) -> GeoJSON {
        return { name ->
            handler(name.toSlug().value) ?: throw IOException("can't find constituency boundary for $name")
        }
    }
}

object LocalityBoundaries {
    operator fun invoke(handler: (String) -> GeoJSON?): (PlaceName) -> GeoJSON {
        return { name ->
            handler(name.toSlug().value) ?: throw IOException("can't find locality boundary for $name")
        }
    }
}

object BeachBoundaries {
    operator fun invoke(handler: (String) -> GeoJSON?): (BeachName) -> GeoJSON? {
        return { name -> handler(name.value.kebabCase()) }
    }
}

object ShellfishBoundaries {
    operator fun invoke(handler: (String) -> GeoJSON?): (ShellfisheryName) -> GeoJSON? {
        return { name -> handler(name.value.kebabCase()) }
    }
}

object Boundaries {
    operator fun invoke(handler: HttpHandler): (String) -> GeoJSON? {
        return { name ->
            val uri = Uri.of("$name.json")
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

object LocalityRankings {
    operator fun invoke(handler: HttpHandler): () -> List<PlaceRank> {
        return {
            val response = handler(Request(Method.GET, "spills-by-locality.json"))

            TotpJson.mapper.readSimpleList(response.bodyString())
                .mapIndexed { r, it ->
                    PlaceRank(
                        rank = r + 1,
                        placeName = PlaceName(it["locality"] as String),
                        overflowCount = (it["total_spills"] as Double).toInt(),
                        zeroMonitoringCount = (it["monitoring_zero_count"] as Int),
                        duration = fromEDMHours((it["total_hours"] as Double)),
                        countDelta = (it["spills_increase"] as Double).toInt(),
                        durationDelta = fromEDMHours(it["hours_increase"] as Double),
                        csoCount = (it["cso_count"] as Int),
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
    val loc: Coordinates,
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
                        loc = Coordinates(it["lat"] as Double, it["lon"] as Double),
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


object ShellfishRankings {
    operator fun invoke(handler: HttpHandler): () -> List<ShellfishRank> {
        return {
            val response = handler(Request(Method.GET, "spills-by-shellfish.json"))

            TotpJson.mapper.readSimpleList(response.bodyString())
                .mapIndexed { r, it ->
                    ShellfishRank(
                        rank = r + 1,
                        shellfishery = ShellfisheryName(it["shellfishery"] as String),
                        company = CompanyName(it["company_name"] as String),
                        duration = fromEDMHours(it["total_spill_hours"] as Double),
                        count = (it["total_count"] as Double).toInt(),
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
                        places = (it["localities"] as List<String>).map { PlaceName.of(it) },
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
    { name: ConstituencyName -> source().filter { it.constituency == name } }

fun placeCSOs(source: () -> List<CSOTotals>) = { name: PlaceName -> source().filter { name in it.places } }


fun waterwayCSOs(source: () -> List<CSOTotals>) =
    { name: WaterwaySlug, company: Slug ->
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

fun placeRivers(csos: () -> List<CSOTotals>, rivers: () -> List<RiverRank>) = { name: PlaceName ->
    val waterways = csos()
        .asSequence()
        .filter { name in it.places }
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

object ConstituencyNeighbours {
    operator fun invoke(handler: HttpHandler): (ConstituencyName) -> List<ConstituencyName> {
        return { wanted ->
            val response = handler(Request(Method.GET, "constituency-neighbours.json"))

            TotpJson.mapper.readSimpleList(response.bodyString())
                .map {
                    ConstituencyName(it["pcon24nm"] as String) to
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
                        constituency = ConstituencyName(it["pcon24nm"] as String),
                        beach = (it["beach_name"] as String?)?.let { BeachName(it) }
                    )
                }
        }
    }
}


data class ShellfishCSO(
    val year: Int,
    val company: CompanyName,
    val sitename: String,
    val shellfishery: ShellfisheryName,
    val count: Int,
    val duration: Duration,
    val reporting: Number,
    val waterway: WaterwayName,
    val location: Coordinates,
    val constituency: ConstituencyName,
)


object ShellfishCSOs {
    operator fun invoke(handler: HttpHandler): () -> List<ShellfishCSO> {
        val response = handler(Request(Method.GET, "csos-by-shellfish.json"))

        return {
            TotpJson.mapper.readSimpleList(response.bodyString())
                .map {
                    ShellfishCSO(
                        year = it["reporting_year"] as Int,
                        company = CompanyName(it["company_name"] as String),
                        sitename = it["site_name"] as String,
                        shellfishery = ShellfisheryName(it["shellfishery"] as String),
                        count = (it["spill_count"] as Double).toInt(),
                        duration = fromEDMHours(it["total_spill_hours"] as Double),
                        reporting = it["reporting_pct"] as Double,
                        waterway = WaterwayName(it["receiving_water"] as String),
                        location = Coordinates(it["lat"] as Double, it["lon"] as Double),
                        constituency = ConstituencyName(it["pcon24nm"] as String),
                    )
                }
        }
    }
}
