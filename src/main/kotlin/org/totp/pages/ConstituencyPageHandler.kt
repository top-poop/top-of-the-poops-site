package org.totp.pages

import org.http4k.core.*
import org.http4k.lens.Header.LOCATION
import org.http4k.lens.Path
import org.http4k.lens.PathLens
import org.http4k.lens.value
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.THE_YEAR
import org.totp.db.StreamData
import org.totp.extensions.kebabCase
import org.totp.http4k.pageUriFrom
import org.totp.http4k.removeQuery
import org.totp.model.PageViewModel
import org.totp.model.data.*
import org.totp.text.csv.readCSV
import java.text.NumberFormat
import java.time.*


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
        year = THE_YEAR,
        locationCount = filter { it.count > 0 }.size,
        companies = map { it.cso.company }.toSet().sorted(),
        count = RenderableCount(sumOf { it.count }),
        duration = (map { it.duration }
            .reduceOrNull { acc, duration -> acc.plus(duration) }
            ?: Duration.ZERO).toRenderable(),
        csoCount = size,
        lowReportingCount = count { it.reporting.toDouble() < 50.0 },
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
    val tags: List<String>,
    val via: String,
    val twitterImageUri: Uri? = null,
)

data class ConstituencyPageLiveData(
    val total: RenderableConstituencyLiveTotal,
    val year: Int,
    val latest: LocalDateTime
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


object ConstituencyAsParameterRedirectFilter {
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

object ConstituencyBoundaryChangesRedirectFilter {

    data class ChangedConstituency(val old: ConstituencyName, val new: ConstituencyName)

    private val changedConstituencies = readCSV(resource = "/data/changed-constituencies.csv", mapper = {
        ChangedConstituency(ConstituencyName(it[1]), ConstituencyName(it[2]))
    })

    private val constituencyRedirects =
        changedConstituencies
            .filter { it.old != it.new }
            .filter { it.old.toSlug() != it.new.toSlug() }
            .associateBy { it.old.toSlug() }

    operator fun invoke(slug: PathLens<ConstituencySlug>): Filter {
        return Filter { next ->
            { request ->
                val given = slug(request)
                val redirect = constituencyRedirects[given]

                if (redirect != null) {
                    val target = redirect.new.toSlug()
                    Response(Status.TEMPORARY_REDIRECT)
                        .with(
                            LOCATION of request.uri.path("/constituency/${target}")
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
        clock: Clock,
        renderer: TemplateRenderer,
        constituencySpills: (ConstituencyName) -> List<CSOTotals>,
        mpFor: (ConstituencyName) -> MP?,
        constituencyBoundary: (ConstituencyName) -> GeoJSON,
        constituencyLiveAvailable: () -> Set<ConstituencyName>,
        constituencyNeighbours: (ConstituencyName) -> List<ConstituencyName>,
        constituencyRank: (ConstituencyName) -> ConstituencyRank?,
        constituencyRivers: (ConstituencyName) -> List<RiverRank>,
        constituencyLiveTotals: (ConstituencyName, LocalDate, LocalDate) -> StreamData.ConstituencyLiveTotal,
        liveDataLatest: () -> Instant,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        val constituencySlug: PathLens<ConstituencySlug> =
            Path.value(ConstituencySlug).of("constituency", "The constituency")

        return ConstituencyAsParameterRedirectFilter()
            .then(ConstituencyBoundaryChangesRedirectFilter(constituencySlug))
            .then { request: Request ->
                val slug = constituencySlug(request)

                slugToConstituency[slug]?.let { constituencyName ->

                    val liveAvailable = constituencyLiveAvailable()

                    val renderableConstituencies = slugToConstituency
                        .map {
                            it.value.toRenderable(
                                current = it.key == slug,
                                haveLive = liveAvailable.contains(it.value)
                            )
                        }

                    val list = constituencySpills(constituencyName).sortedByDescending { it.duration }

                    val neighbours = constituencyNeighbours(constituencyName)
                        .sorted()
                        .mapNotNull { constituencyRank(it) }
                        .map { it.toRenderable(mpFor) }

                    val summary = list.summary()

                    val rivers2 = constituencyRivers(constituencyName)
                    val rivers = rivers2.take(5)
                        .map { it.toRenderable() }

                    val mp = mpFor(constituencyName)

                    Response(Status.OK)
                        .with(
                            viewLens of ConstituencyPage(
                                pageUriFrom(request).removeQuery(),
                                constituencyName.toRenderable(current = true),
                                mp = mp,
                                share(mp, summary, constituencyName, slug, pageUriFrom(request)),
                                summary,
                                constituencyBoundary(constituencyName),
                                list.map {
                                    it.toRenderable()
                                },
                                renderableConstituencies,
                                live = if (liveAvailable.contains(constituencyName)) {
                                    val now = LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Europe/London"))
                                    ConstituencyPageLiveData(
                                        latest = LocalDateTime.ofInstant(liveDataLatest(), ZoneId.of("Europe/London")),
                                        year = now.year,
                                        total = constituencyLiveTotals(
                                            constituencyName,
                                            LocalDate.of(now.year, 1, 1),
                                            now.toLocalDate()
                                        ).toRenderable(),
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

    private fun share(
        mp: MP?,
        summary: PollutionSummary,
        constituencyName: ConstituencyName,
        slug: ConstituencySlug,
        uri: Uri
    ): SocialShare {

        val numberFormat = NumberFormat.getIntegerInstance()
        val formatted = numberFormat.format(summary.duration.hours)

        return mp?.let { mp ->
            SocialShare(
                uri,
                text = "Hey ${mp.name}! What are you doing about the $formatted hours of sewage pollution in $constituencyName",
                tags = listOf("sewage"),
                via = "sewageuk",
                twitterImageUri = Uri.of("https://top-of-the-poops.org/badges/constituency/${slug}-2024.png")
            )
        } ?: SocialShare(
            uri,
            text = "$constituencyName had $formatted hours of sewage pollution in ${summary.year}",
            tags = listOf("sewage"),
            via = "sewageuk",
            twitterImageUri = Uri.of("https://top-of-the-poops.org/badges/constituency/${slug}-2024.png")
        )
    }
}

fun ConstituencyName.toRenderable(
    current: Boolean = false,
    haveLive: Boolean = false,
    linkLive: Boolean = false
): RenderableConstituency {
    val slug = this.toSlug()
    return RenderableConstituency(
        name = this,
        current = current,
        slug = slug,
        uri = slug.let {
            if (linkLive) {
                Uri.of("/constituency/$it/live")
            } else {
                Uri.of("/constituency/$it")
            }
        },
        live = haveLive,
    )
}

data class RenderableConstituencyLiveTotal(
    val constituency: RenderableConstituency,
    val duration: RenderableDuration,
    val count: RenderableCount,
)

fun StreamData.ConstituencyLiveTotal.toRenderable(): RenderableConstituencyLiveTotal {
    return RenderableConstituencyLiveTotal(
        constituency = this.constituency.toRenderable(linkLive = true),
        duration = this.duration.toRenderable(),
        count = RenderableCount(csoCount),
    )
}