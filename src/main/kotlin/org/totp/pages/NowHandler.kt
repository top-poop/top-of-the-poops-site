package org.totp.pages

import org.http4k.core.*
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.db.StreamData
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel

class NowPage(
    uri: Uri,
    val summary: StreamData.StreamOverflowSummary
) : PageViewModel(uri)

object NowHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        streamData: StreamData,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request: Request ->

            Response(Status.OK)
                .with(
                    viewLens of NowPage(pageUriFrom(request), summary = streamData.summary()),
                )
        }
    }
}

