package org.totp.pages

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.lens.Header.LOCATION
import org.http4k.lens.Path
import org.http4k.lens.value
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.extensions.kebabCase
import org.totp.http4k.pageUriFrom
import org.totp.http4k.removeQuery
import org.totp.model.PageViewModel
import org.totp.model.data.CSOTotals
import org.totp.model.data.CompanyName
import org.totp.model.data.ConstituencyLiveData
import org.totp.model.data.ConstituencyName
import org.totp.model.data.ConstituencySlug
import org.totp.model.data.Coordinates
import org.totp.model.data.GeoJSON
import org.totp.model.data.RenderableCompany
import org.totp.model.data.RiverRank
import org.totp.model.data.toSlug
import org.totp.model.data.toRenderable
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
)

fun List<CSOTotals>.summary(): PollutionSummary {
    return PollutionSummary(
        year = 2022,
        locationCount = filter { it.count > 0 }.size,
        companies = map { it.cso.company }.toSet().sorted(),
        count = sumOf { it.count }.let { RenderableCount(it) },
        duration = (map { it.duration }
            .reduceOrNull { acc, duration -> acc.plus(duration) }
            ?: Duration.ZERO)
            .let {
                it.toRenderable()
            }
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
    val data: ConstituencyLiveData,
    val uri: Uri,
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
    val name: ConstituencyName,
    val share: SocialShare,
    val summary: PollutionSummary,
    val geojson: GeoJSON,
    val csos: List<RenderableCSOTotal>,
    val constituencies: List<RenderableConstituency>,
    val live: ConstituencyPageLiveData?,
    val neighbours: List<RenderableConstituencyRank>,
    val rivers: List<RenderableRiverRank>,
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
        mpFor: (ConstituencyName) -> MP,
        constituencyBoundary: (ConstituencyName) -> GeoJSON,
        constituencyLiveData: (ConstituencyName) -> ConstituencyLiveData?,
        constituencyLiveAvailable: () -> List<ConstituencyName>,
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

                val liveAvailable = constituencyLiveAvailable().toSet()

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

                Response(Status.OK)
                    .with(
                        viewLens of ConstituencyPage(
                            pageUriFrom(request).removeQuery(),
                            constituencyName,
                            SocialShare(
                                pageUriFrom(request),
                                text = "$constituencyName had ${numberFormat.format(summary.count.count)} sewage overflows in ${summary.year} - ${
                                    mpFor(
                                        constituencyName
                                    ).handle
                                }",
                                cta = "Share $constituencyName sewage horrors",
                                tags = listOf("sewage"),
                                via = "sewageuk",
                                twitterImageUri = Uri.of("https://top-of-the-poops.org/badges/constituency/${slug}.png")
                            ),
                            summary,
                            constituencyBoundary(constituencyName),
                            list.map {
                                it.toRenderable()
                            },
                            renderableConstituencies,
                            if (liveAvailable.contains(constituencyName)) {
                                constituencyLiveData(constituencyName)?.let {
                                    ConstituencyPageLiveData(
                                        it,
                                        Uri.of("/data/live/constituencies/$slug.json")
                                    )
                                }
                            } else null,
                            neighbours,
                            rivers,
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
