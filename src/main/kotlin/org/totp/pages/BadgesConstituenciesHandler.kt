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
import org.totp.extensions.Defect
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import org.totp.model.data.CSOTotals
import org.totp.model.data.ConstituencyContact
import org.totp.model.data.ConstituencyName
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
        constituencyContacts: () -> List<ConstituencyContact>,
        constituencyBoundaries: (ConstituencyName) -> GeoJSON,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request ->
            val rankings = constituencyRankings()
            val mps = constituencyContacts().associateBy { it.constituency }

            Response(Status.OK)
                .with(
                    viewLens of BadgesConstituenciesPage(
                        pageUriFrom(request),
                        2022,
                        rankings.sortedBy { it.constituencyName }.map {
                            RenderableConstituencyRank(
                                it.rank,
                                RenderableConstituency.from(it.constituencyName),
                                mps[it.constituencyName]?.mp
                                    ?: throw Defect("We don't have the MP for ${it.constituencyName}"),
                                RenderableCount(it.count),
                                RenderableDuration(it.duration),
                                countDelta = DeltaValue.of(it.countDelta),
                                durationDelta = RenderableDurationDelta(it.durationDelta)
                            )
                        },
                        rankings.map {
                            RenderableConstituency.from(it.constituencyName).slug to constituencyBoundaries(it.constituencyName)
                        }
                    )
                )
        }
    }
}