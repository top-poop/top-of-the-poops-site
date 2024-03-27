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
import org.totp.model.data.RiverRank

class BadgesRiversPage(
    uri: Uri,
    val year: Int,
    val rivers: List<RenderableRiverRank>
) : PageViewModel(uri)


object BadgesRiversHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        riverRankings: () -> List<RiverRank>,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request ->

            val rankings = riverRankings()
                .map { it.toRenderable() }

            Response(Status.OK)
                .with(
                    viewLens of BadgesRiversPage(
                        pageUriFrom(request),
                        2023,
                        rankings,
                    ),
                )
        }
    }
}