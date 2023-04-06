package org.totp.pages

import dev.forkhandles.values.StringValue
import dev.forkhandles.values.StringValueFactory
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
import org.totp.extensions.Defect
import org.totp.extensions.kebabCase
import org.totp.http4k.pageUriFrom
import org.totp.http4k.removeQuery
import org.totp.model.PageViewModel
import org.totp.model.data.CSOTotals
import org.totp.model.data.CompanyName
import org.totp.model.data.ConstituencyContact
import org.totp.model.data.ConstituencyLiveData
import org.totp.model.data.ConstituencyName
import org.totp.model.data.Coordinates
import org.totp.model.data.GeoJSON
import org.totp.pages.SiteLocations.waterwayUriFor
import org.totp.text.csv.readCSV
import java.text.NumberFormat
import java.time.Duration


val constituencyNames = readCSV(
    resource = "/data/constituencies.csv",
    mapper = { ConstituencyName(it[0]) }
).toSortedSet(Comparator.comparing { it.value })


class ConstituencySlug(value: String) : StringValue(value) {
    companion object : StringValueFactory<ConstituencySlug>(::ConstituencySlug) {
        fun from(name: ConstituencyName): ConstituencySlug {
            return of(name.value.kebabCase())
        }
    }
}

val slugToConstituency = constituencyNames.associateBy { ConstituencySlug.from(it) }

data class PollutionSummary(
    val year: Int,
    val locationCount: Int,
    val companies: List<CompanyName>,
    val count: RenderableCount,
    val duration: RenderableDuration
) {

    companion object {
        fun from(csos: List<CSOTotals>): PollutionSummary {
            return PollutionSummary(
                year = 2022,
                locationCount = csos
                    .filter { it.count > 0 }
                    .size,
                companies = csos
                    .map { it.cso.company }
                    .toSet()
                    .toList()
                    .sortedBy { it.value },
                count = csos
                    .sumOf { it.count }
                    .let { RenderableCount(it) },
                duration = (csos
                    .map { it.duration }
                    .reduceOrNull { acc, duration -> acc.plus(duration) }
                    ?: Duration.ZERO)
                    .let {
                        RenderableDuration(it)
                    }
            )
        }
    }
}

data class RenderableConstituency(
    val name: ConstituencyName,
    val current: Boolean,
    val slug: ConstituencySlug,
    val uri: Uri,
    val live: Boolean
)

data class SocialShare(
    val uri: Uri,
    val text: String,
    val cta: String,
    val tags: List<String>,
    val via: String,
    val twitterImageUri: Uri? = null
)

data class ConstituencyPageLiveData(
    val data: ConstituencyLiveData,
    val uri: Uri
)

data class RenderableCSO(
    val company: RenderableCompany,
    val sitename: String,
    val waterway: RenderableWaterway,
    val location: Coordinates
)

data class RenderableCSOTotal(
    val constituency: RenderableConstituency,
    val cso: RenderableCSO,
    val count: Int,
    val duration: Duration,
    val reporting: Number
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

object ConstituencyPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        constituencySpills: (ConstituencyName) -> List<CSOTotals>,
        mpFor: (ConstituencyName) -> MP,
        constituencyBoundary: (ConstituencyName) -> GeoJSON,
        constituencyLiveData: (ConstituencyName) -> ConstituencyLiveData?,
        constituencyLiveAvailable: () -> List<ConstituencyName>,
        constituencyNeighbours: (ConstituencyName) -> List<ConstituencyName>,
        constituencyRank: (ConstituencyName) -> ConstituencyRank?
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

                val summary = PollutionSummary.from(list)
                Response(Status.OK)
                    .with(
                        viewLens of ConstituencyPage(
                            pageUriFrom(request).removeQuery(),
                            constituencyName,
                            SocialShare(
                                pageUriFrom(request),
                                text = "$constituencyName had ${numberFormat.format(summary.count.count)} sewage overflows in ${summary.year} - ${mpFor(constituencyName).handle}",
                                cta = "Share $constituencyName sewage horrors",
                                tags = listOf("sewage"),
                                via = "sewageuk",
                                twitterImageUri = Uri.of("https://top-of-the-poops.org/badges/constituency/${slug}.png")
                            ),
                            summary,
                            constituencyBoundary(constituencyName),
                            list.map {
                                RenderableCSOTotal(
                                    it.constituency.toRenderable(),
                                    it.cso.let {
                                        RenderableCSO(
                                            RenderableCompany.from(it.company),
                                            it.sitename,
                                            RenderableWaterway(it.waterway, waterwayUriFor(it.waterway, it.company)),
                                            it.location
                                        )
                                    },
                                    it.count,
                                    it.duration,
                                    it.reporting
                                )
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
                            neighbours
                        )
                    )
            }
                ?: Response(Status.NOT_FOUND)
        }
    }
}

fun ConstituencyName.toRenderable(current: Boolean = false, live: Boolean = false): RenderableConstituency {
    val slug = ConstituencySlug.from(this)
    return RenderableConstituency(
        name = this,
        current = current,
        slug = slug,
        uri = slug.let { Uri.of("/constituency/$it") },
        live = live,
    )
}
