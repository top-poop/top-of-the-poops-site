package org.totp.pages

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.with
import org.http4k.lens.Path
import org.http4k.lens.value
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import org.totp.model.data.BathingCSO
import org.totp.model.data.BathingName
import org.totp.model.data.BathingRank
import org.totp.model.data.BathingSlug
import org.totp.model.data.BeachName
import org.totp.model.data.Coordinates
import org.totp.model.data.GeoJSON
import org.totp.model.data.toSlug
import java.text.NumberFormat
import java.time.Duration


class BathingPage(
    uri: Uri,
    val name: BathingName,
    val summary: PollutionSummary,
    val share: SocialShare,
    val rank: RenderableBathingRank,
    val csos: List<RenderableBathingCSO>,
    val geojson: GeoJSON?,
) : PageViewModel(uri)

fun List<BathingCSO>.summary(): PollutionSummary {
    return PollutionSummary(
        year = 2022,
        locationCount = filter { it.count > 0 }.size,
        companies = map { it.company }.toSet().sorted(),
        count = sumOf { it.count }.let { RenderableCount(it) },
        duration = (map { it.duration }
            .reduceOrNull { acc, duration -> acc.plus(duration) }
            ?: Duration.ZERO).toRenderable()
    )
}

data class RenderableBathingCSO(
    val company: RenderableCompany,
    val sitename: String,
    val count: Int,
    val duration: RenderableDuration,
    val reporting: Number,
    val waterway: RenderableWaterway,
    val location: Coordinates,
    val constituency: RenderableConstituency,
)

fun BathingCSO.toRenderable(): RenderableBathingCSO {
    return RenderableBathingCSO(
        RenderableCompany.from(company),
        sitename,
        count,
        duration.toRenderable(),
        reporting,
        waterway = waterway.toRenderable(company),
        location,
        constituency = constituency.toRenderable()
    )
}


object BathingPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        bathingRankings: () -> List<BathingRank>,
        bathingCSOs: (BathingSlug) -> List<BathingCSO>,
        beachBoundaries: (BeachName) -> GeoJSON?,
    ): HttpHandler {

        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()
        val bathingSlug = Path.value(BathingSlug).of("bathing", "The bathing area, as given in EDM")

        return { request ->

            val bathingArea = bathingSlug(request)

            val csos = bathingCSOs(bathingArea).sortedByDescending { it.duration }

            if (csos.isEmpty()) {
                Response(Status.NOT_FOUND)
            } else {
                val numberFormat = NumberFormat.getIntegerInstance()

                val name = csos.first().bathing

                val rank = bathingRankings().filter { it.beach.toSlug() == bathingArea }.first().toRenderable()
                val geojson = csos.mapNotNull { it.beach }.firstOrNull()?.let { beachBoundaries(it) }

                val summary = csos.summary()
                Response(Status.OK)
                    .with(
                        viewLens of BathingPage(
                            pageUriFrom(request),
                            name,
                            summary = summary,
                            share = SocialShare(
                                pageUriFrom(request),
                                text = "$name had ${numberFormat.format(summary.count.count)} sewage overflows in ${summary.year}",
                                cta = "$name pollution",
                                tags = listOf("sewage"),
                                via = "sewageuk"
                            ),
                            rank,
                            csos.map { it.toRenderable() },
                            geojson,
                        )
                    )
            }
        }
    }
}