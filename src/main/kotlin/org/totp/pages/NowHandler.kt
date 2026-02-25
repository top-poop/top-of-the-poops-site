package org.totp.pages

import org.http4k.core.*
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.db.StreamData
import org.totp.extensions.sumDuration
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import org.totp.model.data.CompanyName
import org.totp.model.data.RenderableCompany
import org.totp.model.data.toRenderable
import java.text.NumberFormat
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class NowPage(
    uri: Uri,
    val share: SocialShare,
    val summary: RenderableStreamOverflowSummary,
    val current: RenderableTotal
) : PageViewModel(uri)

data class RenderableStreamCompanyStatus(
    val company: RenderableCompany,
    val count: StreamData.StreamCSOCount
)

data class RenderableStreamOverflowSummary(
    val count: StreamData.StreamCSOCount,
    val companies: List<RenderableStreamCompanyStatus>
)

data class RenderableTotal(
    val year: Int,
    val current: Boolean,
    val start: RenderableDuration,
    val offline: RenderableDuration,
    val potential: RenderableDuration
)

data class CompanyRenderableTotal(
    val company: RenderableCompany,
    val total: RenderableTotal
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
        clock: Clock,
        renderer: TemplateRenderer,
        streamData: StreamData,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request: Request ->
            val summary = streamData.summary().toRenderable()

            val formatted = NumberFormat.getNumberInstance().format(summary.count.start)

            val today = LocalDate.ofInstant(clock.instant(), ZoneId.of("UTC"))

            val startDate = LocalDate.of(today.year, 1, 1)
            val endDate = today.withDayOfMonth(1).plusMonths(1)

            val totals = streamData.totalsByCompany(startDate, endDate)

            val total = RenderableTotal(
                year = startDate.year,
                current = true,
                start = RenderableDuration(totals.sumDuration { it.bucket.start }),
                offline = RenderableDuration(totals.sumDuration { it.bucket.offline }),
                potential = RenderableDuration(totals.sumDuration { it.bucket.potential })
            )

            Response(Status.OK)
                .with(
                    viewLens of NowPage(
                        pageUriFrom(request),
                        current = total,
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

