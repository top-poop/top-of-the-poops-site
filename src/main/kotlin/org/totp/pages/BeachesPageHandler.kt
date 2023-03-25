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
import org.totp.model.data.BeachRank
import java.text.NumberFormat

class BeachesPage(
    uri: Uri,
    val year: Int,
    val beachRankings: List<BeachRank>
) : PageViewModel(uri)

object BeachesPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        beachRankings: () -> List<BeachRank>,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request: Request ->
            Response(Status.OK)
                .with(
                    viewLens of BeachesPage(
                        pageUriFrom(request),
                        year = 2021,
                        beachRankings().sortedByDescending { it.rank },
                    )
                )
        }
    }
}