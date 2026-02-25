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
import org.totp.THE_YEAR
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import org.totp.model.data.BathingRank
import org.totp.model.data.Coordinates
import org.totp.model.data.RenderableBathingName
import org.totp.model.data.RenderableCompany
import org.totp.model.data.toRenderable
import java.text.NumberFormat
import java.time.Duration

class BeachesPage(
    uri: Uri,
    val year: Int,
    val totalCount: Int,
    val totalDuration: Duration,
    val beachRankings: List<RenderableBathingRank>,
    val polluterRankings: List<BeachPolluter>,
    val share: SocialShare,
) : PageViewModel(uri)


data class BeachPolluter(
    val rank: Int,
    val company: RenderableCompany,
    val count: Int,
    val duration: Duration,
    val countDelta: DeltaValue,
    val durationDelta: RenderableDurationDelta,
)


data class RenderableBathingRank(
    val rank: Int,
    val beach: RenderableBathingName,
    val company: RenderableCompany,
    val count: RenderableCount,
    val duration: RenderableDuration,
    val countDelta: DeltaValue,
    val durationDelta: RenderableDurationDelta,
    val loc: Coordinates,
)

fun BathingRank.toRenderable(): RenderableBathingRank {
    return RenderableBathingRank(
        rank,
        beach.toRenderable(),
        company.toRenderable(),
        RenderableCount(count),
        duration.toRenderable(),
        countDelta,
        RenderableDurationDelta(durationDelta),
        loc = loc
    )
}


object BeachesPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        bathingRankings: () -> List<BathingRank>,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request: Request ->
            val numberFormat = NumberFormat.getNumberInstance()
            val rankings = bathingRankings().sortedBy { it.rank }
            val polluters = rankings.groupBy { it.company }
                .map {
                    BeachPolluter(
                        0,
                        it.key.toRenderable(),
                        count = it.value.sumOf { it.count },
                        duration = it.value.map { it.duration }.reduce { acc, duration -> acc + duration },
                        countDelta = it.value.map { it.countDelta }
                            .reduce { acc, deltaValue -> DeltaValue(acc.value + deltaValue.value) },
                        durationDelta = it.value.map { it.durationDelta }.reduce { acc, duration -> acc + duration }
                            .let { RenderableDurationDelta(it) },
                    )
                }.sortedByDescending {
                    it.duration
                }.mapIndexed { i, p ->
                    p.copy(rank = i + 1)
                }

            val totalDuration = rankings.map { it.duration }.reduce { acc, duration -> acc + duration }
            val totalCount = rankings.map { it.count }.reduce { acc, count -> acc + count }

            Response(Status.OK)
                .with(
                    viewLens of BeachesPage(
                        pageUriFrom(request),
                        year = THE_YEAR,
                        totalCount,
                        totalDuration,
                        rankings.map {
                            it.toRenderable()
                        },
                        polluterRankings = polluters,
                        SocialShare(
                            pageUriFrom(request),
                            "Are the beaches safe for swimming? - ${numberFormat.format(totalCount)} sewage pollution incidents in 2024",
                            listOf("sewage"),
                            via = "sewageuk",
                            twitterImageUri = Uri.of("https://top-of-the-poops.org/badges/home/beaches.png")
                        )

                    )
                )
        }
    }
}