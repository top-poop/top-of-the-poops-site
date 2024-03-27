package org.totp.pages

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.with
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import org.totp.model.data.ConstituencyName
import org.totp.model.data.ConstituencySlug
import org.totp.model.data.GeoJSON

class BadgesConstituenciesPage(
    uri: Uri,
    val year: Int,
    val constituencies: List<RenderableConstituencyRank>,
    val boundaries: List<Pair<ConstituencySlug, GeoJSON>>,
) : PageViewModel(uri)


object BadgesConstituenciesHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        constituencyRankings: () -> List<ConstituencyRank>,
        mpFor: (ConstituencyName) -> MP,
        constituencyBoundaries: (ConstituencyName) -> GeoJSON,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request ->
            val rankings = constituencyRankings()

            Response(Status.OK)
                .with(
                    viewLens of BadgesConstituenciesPage(
                        pageUriFrom(request),
                        2023,
                        rankings.sortedBy { it.constituencyName }.map {
                            RenderableConstituencyRank(
                                it.rank,
                                it.constituencyName.toRenderable(),
                                mpFor(it.constituencyName),
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