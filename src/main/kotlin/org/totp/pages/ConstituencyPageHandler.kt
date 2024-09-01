package org.totp.pages

import org.http4k.core.*
import org.http4k.lens.Header.LOCATION
import org.http4k.lens.Path
import org.http4k.lens.value
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.extensions.kebabCase
import org.totp.http4k.pageUriFrom
import org.totp.http4k.removeQuery
import org.totp.model.PageViewModel
import org.totp.model.data.*
import org.totp.text.csv.readCSV
import java.text.NumberFormat
import java.time.Duration


val constituencyNames = readCSV(
    resource = "/data/constituencies.csv",
    mapper = { ConstituencyName(it[0]) }
).toSortedSet(Comparator.comparing { it.value })


val slugToConstituency = constituencyNames.associateBy { it.toSlug() }


data class PollutionSummary(
    val year: Int,
    val locationCount: Int,
    val companies: List<CompanyName>,
    val count: RenderableCount,
    val duration: RenderableDuration,
    val csoCount: Int,
    val lowReportingCount: Int,
    val zeroReportingCount: Int,
)

fun List<CSOTotals>.summary(): PollutionSummary {
    return PollutionSummary(
        year = 2023,
        locationCount = filter { it.count > 0 }.size,
        companies = map { it.cso.company }.toSet().sorted(),
        count = RenderableCount(sumOf { it.count }),
        duration = (map { it.duration }
            .reduceOrNull { acc, duration -> acc.plus(duration) }
            ?: Duration.ZERO).toRenderable(),
        csoCount = size,
        lowReportingCount = count { it.reporting.toDouble() < 0.5 },
        zeroReportingCount = count { it.reporting.toDouble() == 0.0 }
    )
}


data class RenderableConstituency(
    val name: ConstituencyName,
    val current: Boolean,
    val slug: ConstituencySlug,
    val uri: Uri,
    val live: Boolean,
)

data class SocialShare(
    val uri: Uri,
    val text: String,
    val cta: String,
    val tags: List<String>,
    val via: String,
    val twitterImageUri: Uri? = null,
)

data class ConstituencyPageLiveData(
    val csoUri: Uri,
    val rainfallUri: Uri
)

data class RenderableCSO(
    val company: RenderableCompany,
    val sitename: String,
    val waterway: RenderableWaterway,
    val location: Coordinates,
)

data class RenderableCSOTotal(
    val constituency: RenderableConstituency,
    val cso: RenderableCSO,
    val count: RenderableCount,
    val duration: RenderableDuration,
    val reporting: Number,
)

class ConstituencyPage(
    uri: Uri,
    val constituency: RenderableConstituency,
    val mp: MP?,
    val share: SocialShare,
    val summary: PollutionSummary,
    val geojson: GeoJSON,
    val csos: List<RenderableCSOTotal>,
    val constituencies: List<RenderableConstituency>,
    val live: ConstituencyPageLiveData?,
    val neighbours: List<RenderableConstituencyRank>,
    val rivers: List<RenderableRiverRank>
) :
    PageViewModel(uri)


object ConstituencyRedirectFilter {
    operator fun invoke(): Filter {
        val constituencyName = Path.value(ConstituencyName).of("constituency", "The constituency")

        return Filter { next ->
            { request ->
                val suppliedName = constituencyName(request)
                if (constituencyNames.contains(suppliedName)) {
                    val redirect = request.uri.path.replace(suppliedName.value, suppliedName.value.kebabCase())
                    Response(Status.TEMPORARY_REDIRECT)
                        .with(
                            LOCATION of request.uri.path(redirect)
                        )
                } else {
                    next(request)
                }
            }
        }
    }
}

fun CSOTotals.toRenderable(): RenderableCSOTotal {
    return RenderableCSOTotal(
        constituency.toRenderable(),
        cso.let {
            RenderableCSO(
                it.company.toRenderable(),
                it.sitename,
                it.waterway.toRenderable(it.company),
                it.location
            )
        },
        RenderableCount(count),
        duration.toRenderable(),
        reporting
    )
}

object ConstituencyPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        constituencySpills: (ConstituencyName) -> List<CSOTotals>,
        mpFor: (ConstituencyName) -> MP?,
        constituencyBoundary: (ConstituencyName) -> GeoJSON,
        constituencyLiveAvailable: () -> Set<ConstituencyName>,
        constituencyNeighbours: (ConstituencyName) -> List<ConstituencyName>,
        constituencyRank: (ConstituencyName) -> ConstituencyRank?,
        constituencyRivers: (ConstituencyName) -> List<RiverRank>,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        val constituencySlug = Path.value(ConstituencySlug).of("constituency", "The constituency")
        val numberFormat = NumberFormat.getIntegerInstance()

        return ConstituencyRedirectFilter().then { request: Request ->
            val slug = constituencySlug(request)

            slugToConstituency[slug]?.let { constituencyName ->

                val liveAvailable = constituencyLiveAvailable()

                val renderableConstituencies = slugToConstituency
                    .map {
                        it.value.toRenderable(
                            current = it.key == slug,
                            live = liveAvailable.contains(it.value)
                        )
                    }

                val list = constituencySpills(constituencyName).sortedByDescending { it.duration }

                val neighbours = constituencyNeighbours(constituencyName)
                    .sorted()
                    .map { constituencyRank(it) }
                    .filterNotNull()
                    .map { it.toRenderable(mpFor) }

                val summary = list.summary()

                val rivers2 = constituencyRivers(constituencyName)
                val rivers = rivers2.take(5)
                    .map { it.toRenderable() }

                val mp = mpFor(
                    constituencyName
                )

                val formattedHours = numberFormat.format(summary.duration.hours)

                Response(Status.OK)
                    .with(
                        viewLens of ConstituencyPage(
                            pageUriFrom(request).removeQuery(),
                            constituencyName.toRenderable(current = true),
                            mp = mp,
                            mp?.let { mp ->
                                SocialShare(
                                    pageUriFrom(request),
                                    text = null?.let { handle ->
                                        "Hey ${handle}! What are you doing about the $formattedHours hours of sewage pollution in $constituencyName"
                                    } ?: "Hey ${mp.name}! What are you doing about the $formattedHours hours of sewage pollution in $constituencyName",
                                    cta = "Tell ${mp.name} what you think",
                                    tags = listOf("sewage"),
                                    via = "sewageuk",
                                    twitterImageUri = Uri.of("https://top-of-the-poops.org/badges/constituency/${slug}-2023.png")
                                )
                            } ?: SocialShare(
                                pageUriFrom(request),
                                text = "$constituencyName had $formattedHours hours of sewage pollution in ${summary.year}",
                                cta = "Share $constituencyName sewage horrors",
                                tags = listOf("sewage"),
                                via = "sewageuk",
                                twitterImageUri = Uri.of("https://top-of-the-poops.org/badges/constituency/${slug}-2023.png")
                            ),
                            summary,
                            constituencyBoundary(constituencyName),
                            list.map {
                                it.toRenderable()
                            },
                            renderableConstituencies,
                            live = if (liveAvailable.contains(constituencyName)) {
                                ConstituencyPageLiveData(
                                    csoUri = Uri.of("/live/thames-water/events/constituency/$slug"),
                                    rainfallUri = Uri.of("/live/environment-agency/rainfall/$slug"),
                                )
                            } else null,
                            neighbours = neighbours,
                            rivers = rivers,
                        )
                    )
            }
                ?: Response(Status.NOT_FOUND)
        }
    }
}

fun ConstituencyName.toRenderable(current: Boolean = false, live: Boolean = false): RenderableConstituency {
    val slug = this.toSlug()
    return RenderableConstituency(
        name = this,
        current = current,
        slug = slug,
        uri = slug.let { Uri.of("/constituency/$it") },
        live = live,
    )
}
