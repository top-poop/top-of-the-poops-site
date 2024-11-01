package org.totp.pages

import org.http4k.core.*
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel

class SupportUsPage(
    uri: Uri,
) : PageViewModel(uri)

object SupportUsHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request: Request ->

            Response(Status.OK)
                .with(
                    viewLens of SupportUsPage(pageUriFrom(request)),
                )
        }
    }
}

