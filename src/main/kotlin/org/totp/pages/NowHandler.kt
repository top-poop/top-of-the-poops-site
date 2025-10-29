package org.totp.pages

import org.http4k.core.*
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.db.StreamData
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import java.text.NumberFormat

class NowPage(
    uri: Uri,
    val share: SocialShare,
    val summary: StreamData.StreamOverflowSummary
) : PageViewModel(uri)

object NowHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        streamData: StreamData,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request: Request ->
            val summary = streamData.summary()

            val formatted = NumberFormat.getNumberInstance().format(summary.count.start)
            Response(Status.OK)
                .with(
                    viewLens of NowPage(
                        pageUriFrom(request),
                        summary = summary,
                        share = SocialShare(
                            pageUriFrom(request),
                            text = "$formatted sewage overflows happening right now",
                            tags = listOf("sewage"),
                            via = "sewageuk",
                            twitterImageUri = Uri.of("https://top-of-the-poops.org/assets/images/logos/live-map-og-image.jpg")
                        )
                    ),
                )
        }
    }
}

