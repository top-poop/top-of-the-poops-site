package org.totp.pages

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.query
import org.http4k.core.with
import org.http4k.lens.Query
import org.http4k.lens.boolean
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import org.totp.model.data.RiverRank
import org.totp.model.data.WaterwayName
import org.totp.pages.SiteLocations.waterwayUriFor
import java.time.Duration

class RiversPage(
    uri: Uri,
    val year: Int,
    val totalCount: Int,
    val totalDuration: Duration,
    val showingSummary: Boolean,
    val showAllUri: Uri,
    val riverRankings: List<RenderableRiverRank>,
) : PageViewModel(uri)


data class RenderableWaterway(val name: WaterwayName, val uri: Uri)

data class RenderableCount(val count:Int) {

    val perDay = count / 365.0
    val perWeek = count / 52.0
    val perMonth = count / 12.0

    val useDays = perDay > 1
    val useWeeks = perWeek > 1
}

data class RenderableRiverRank(
    val rank: Int,
    val river: RenderableWaterway,
    val company: RenderableCompany,
    val count: RenderableCount,
    val duration: RenderableDuration,
    val countDelta: DeltaValue,
    val durationDelta: RenderableDurationDelta
)

fun RiverRank.toRenderable(): RenderableRiverRank {
    val waterwaySlug = WaterwaySlug.from(river)
    val companySlug = CompanySlug.from(company)
    return RenderableRiverRank(
        rank,
        RenderableWaterway(river, Uri.of("/waterway/$companySlug/$waterwaySlug")),
        RenderableCompany.from(company),
        RenderableCount(count),
        RenderableDuration(duration),
        countDelta,
        RenderableDurationDelta(durationDelta)
    )
}

object RiversPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        riverRankings: () -> List<RiverRank>,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()
        val allLens = Query.boolean().defaulted("all", false, "Show full list of rivers");

        return { request: Request ->

            val showAll = allLens(request)

            val rankings = riverRankings().sortedBy { it.rank }

            val totalDuration = rankings.map { it.duration }.reduce { acc, duration -> acc + duration }
            val totalCount = rankings.map { it.count }.reduce { acc, count -> acc + count }

            val display = if (showAll) {
                rankings.filter {
                    it.duration > Duration.ofDays(1)
                }
            } else {
                rankings.take(20)
            }

            Response(Status.OK)
                .with(
                    viewLens of RiversPage(
                        pageUriFrom(request),
                        year = 2022,
                        totalCount,
                        totalDuration,
                        showingSummary = !showAll,
                        showAllUri = Uri.of(request.uri.path).query("all", "true"),
                        display.map {
                            it.toRenderable()
                        },
                    )
                )
        }
    }
}