package org.totp.pages

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.with
import org.http4k.lens.Path
import org.http4k.lens.value
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.THE_YEAR
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import org.totp.model.data.ConstituencyName
import org.totp.model.data.GeoJSON
import org.totp.model.data.ShellfishCSO
import org.totp.model.data.ShellfishRank
import org.totp.model.data.ShellfishSlug
import org.totp.model.data.ShellfisheryName
import org.totp.model.data.toRenderable
import org.totp.model.data.toSlug
import java.text.NumberFormat
import java.time.Duration


class ShellfisheryPage(
    uri: Uri,
    val name: ShellfisheryName,
    val share: SocialShare,
    val summary: PollutionSummary,
    val rank: RenderableShellfishRank,
    val constituencies: List<RenderableConstituencyRank>,
    val boundaries: List<GeoJSON>,
    val csos: List<RenderableCSOTotal>,
) :
    PageViewModel(uri)


fun List<ShellfishCSO>.summary(): PollutionSummary {
    return PollutionSummary(
        year = THE_YEAR,
        locationCount = filter { it.count > 0 }.size,
        companies = map { it.company }.toSet().sorted(),
        count = sumOf { it.count }.let { RenderableCount(it) },
        duration = (map { it.duration }
            .reduceOrNull { acc, duration -> acc.plus(duration) }
            ?: Duration.ZERO).toRenderable(),
        csoCount = size,
        lowReportingCount = count { it.reporting.toDouble() < 0.5 },
        zeroReportingCount = count { it.reporting.toDouble() == 0.0 }
    )
}

fun ShellfishCSO.toRenderable(): RenderableCSOTotal {
    return RenderableCSOTotal(
        constituency = constituency.toRenderable(),
        cso = RenderableCSO(
            company = company.toRenderable(),
            sitename,
            waterway = waterway.toRenderable(company),
            location,
        ),
        count = RenderableCount(count),
        duration = duration.toRenderable(),
        reporting = reporting,
    )
}

object ShellfisheryPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        shellfishRankings: () -> List<ShellfishRank>,
        shellfisheryBoundaries: (ShellfisheryName) -> GeoJSON?,
        shellfishSpills: (ShellfishSlug) -> List<ShellfishCSO>,
        mpFor: (ConstituencyName) -> MP,
        constituencyRank: (ConstituencyName) -> ConstituencyRank?,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        val shellfishSlug = Path.value(ShellfishSlug).of("area", "The shellfish area")

        return { request: Request ->

            val numberFormat = NumberFormat.getIntegerInstance()

            val area = shellfishSlug(request)
            val spills = shellfishSpills(area)

            if (spills.isEmpty()) {
                Response(Status.NOT_FOUND)
            } else {

                val name = spills.first().shellfishery

                val rank = shellfishRankings().filter { it.shellfishery.toSlug() == area }.first().toRenderable()

                val boundaries = listOf(name).mapNotNull { shellfisheryBoundaries(it) }

                val constituencies = spills
                    .map { it.constituency }
                    .toSet()
                    .sorted()
                    .map {
                        constituencyRank(it)
                    }
                    .filterNotNull()
                    .map {
                        it.toRenderable(mpFor)
                    }

                val summary = spills.summary()

                Response(Status.OK)
                    .with(
                        viewLens of ShellfisheryPage(
                            pageUriFrom(request),
                            name,
                            SocialShare(
                                pageUriFrom(request),
                                text = "$name had ${numberFormat.format(summary.count.count)} sewage overflows in ${summary.year}",
                                cta = "$name pollution",
                                tags = listOf("sewage"),
                                via = "sewageuk",
                                twitterImageUri = Uri.of("https://top-of-the-poops.org/badges/shellfishery/${area}")
                            ),
                            summary = summary,
                            constituencies = constituencies,
                            rank = rank,
                            boundaries = boundaries,
                            csos = spills
                                .sortedByDescending { it.duration }
                                .map {
                                    it.toRenderable()
                                })
                    )
            }
        }
    }
}