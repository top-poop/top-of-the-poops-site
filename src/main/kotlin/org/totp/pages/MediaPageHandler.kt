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
import org.totp.model.data.MediaAppearance

@Suppress("unused")
class MediaPage(
    uri: Uri,
    val appearances: List<MediaAppearance>,
    val share: SocialShare
) : PageViewModel(uri)


object MediaPageHandler {

    operator fun invoke(
        renderer: TemplateRenderer,
        appearances: () -> List<MediaAppearance>
    ): HttpHandler {

        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request ->
            Response(Status.OK)
                .with(
                    viewLens of MediaPage(
                        pageUriFrom(request),
                        appearances().sortedByDescending { it.date },
                        SocialShare(
                            pageUriFrom(request),
                            "Wow sewage is a really serious issue in England and Wales right now. What is your MP and @Ofwat doing about it?",
                            listOf("sewage"),
                            via = "@sewageuk"
                        )
                    )
                )
        }
    }
}