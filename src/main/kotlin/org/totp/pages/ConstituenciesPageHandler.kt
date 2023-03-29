package org.totp.pages

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.with
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import java.time.Duration

class ConstituenciesPage(
    uri: Uri,
    var year: Int,
    val constituencyRankings: List<RenderableConstituencyRank>
) : PageViewModel(uri)

class RenderableConstituencyRank(
    val rank: Int,
    val constituency: RenderableConstituency,
    val mp: MP,
    val count: Int,
    val duration: Duration,
    val countDelta: Int,
    val durationDelta: Duration
)

object ConstituenciesPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        consituencyRankings: () -> List<ConstituencyRank>,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request: Request ->
            Response(Status.OK)
                .with(
                    viewLens of ConstituenciesPage(
                        pageUriFrom(request),
                        year = 2021,
                        consituencyRankings().sortedBy { it.rank }.map {
                            RenderableConstituencyRank(
                                it.rank,
                                RenderableConstituency.from(it.constituencyName ),
                                it.mp,
                                it.count,
                                it.duration,
                                countDelta = it.countDelta,
                                durationDelta = it.durationDelta
                            )
                        },
                    )
                )
        }
    }
}