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
import org.totp.model.data.BeachRank
import kotlin.math.floor

class BadgesHomePage(
    uri: Uri,
    val year: Int,
    val count: RenderableCount,
    val duration: RenderableDuration,

    val beachCount: RenderableCount,
    val beachDuration: RenderableDuration,
    val beaches: List<RenderableBeachRank>,
) : PageViewModel(uri)


object BadgesHomeHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        constituencyRankings: () -> List<ConstituencyRank>,
        beachRankings: () -> List<BeachRank>,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request ->

            val rankings = constituencyRankings()
            val totalSpills = rankings.sumOf { it.count }
            val totalSpillsRounded = (floor(totalSpills / 1000.0) * 1000).toInt()

            val totalDuration = rankings.map { it.duration }.reduce { acc, duration -> acc + duration }

            val beaches = beachRankings()
            val beachDuration = beaches.map { it.duration }.reduce { acc, duration -> acc + duration }
            val beachCount = beaches.map { it.count }.reduce { acc, count -> acc + count }

            Response(Status.OK)
                .with(
                    viewLens of BadgesHomePage(
                        pageUriFrom(request),
                        2022,
                        RenderableCount(totalSpillsRounded),
                        RenderableDuration(totalDuration),

                        beachCount = RenderableCount(beachCount),
                        beachDuration = RenderableDuration(beachDuration),
                        beaches = beaches.map {
                            it.toRenderable()
                        }
                    )
                )
        }
    }
}