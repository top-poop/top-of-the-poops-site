package org.totp.pages

import org.http4k.core.*
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.db.StreamData
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import org.totp.model.data.CompanyName
import org.totp.model.data.RenderableCompany
import org.totp.model.data.toRenderable
import java.text.NumberFormat

class NowPage(
    uri: Uri,
    val share: SocialShare,
    val summary: RenderableStreamOverflowSummary
) : PageViewModel(uri)

data class RenderableStreamCompanyStatus(
    val company: RenderableCompany,
    val count: StreamData.StreamCSOCount
)

data class RenderableStreamOverflowSummary(
    val count: StreamData.StreamCSOCount,
    val companies: List<RenderableStreamCompanyStatus>
)

fun StreamData.StreamOverflowSummary.toRenderable(): RenderableStreamOverflowSummary {
    return RenderableStreamOverflowSummary(
        count = this.count,
        companies = this.companies.map {
            val companyName = it.company.asCompanyName() ?: CompanyName("Unknown")
            RenderableStreamCompanyStatus(companyName.toRenderable(), it.count)
        }
    )
}

object NowHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        streamData: StreamData,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request: Request ->
            val summary = streamData.summary().toRenderable()

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

