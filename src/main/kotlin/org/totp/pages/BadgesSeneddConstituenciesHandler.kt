package org.totp.pages

import org.http4k.core.*
import org.http4k.lens.Path
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.THE_YEAR
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import org.totp.model.data.GeoJSON
import org.totp.model.data.SeneddConstituencyName
import org.totp.model.data.Slug

class BadgesSeneddConstituenciesPage(
    uri: Uri,
    val year: Int,
    val constituencies: List<RenderableSeneddRank>,
    val boundaries: List<Pair<Slug, GeoJSON>>,
) : PageViewModel(uri)


object BadgesSeneddConstituenciesHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        constituencyRankings: () -> List<SeneddConstituencyRank>,
        constituencyBoundaries: (SeneddConstituencyName) -> GeoJSON,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request ->
            val rankings = constituencyRankings()

            Response(Status.OK)
                .with(
                    viewLens of BadgesSeneddConstituenciesPage(
                        pageUriFrom(request),
                        THE_YEAR,
                        rankings.sortedBy { it.constituencyName }.map {
                            RenderableSeneddRank(
                                it.rank,
                                it.constituencyName.toRenderable(),
                                RenderableCount(it.count),
                                it.duration.toRenderable(),
                                countDelta = DeltaValue.of(it.countDelta),
                                durationDelta = RenderableDurationDelta(it.durationDelta)
                            )
                        },
                        rankings.map {
                            it.constituencyName.toRenderable().slug to constituencyBoundaries(it.constituencyName)
                        }
                    )
                )
        }
    }
}