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
import java.time.Duration

class BeachesPage(
    uri: Uri,
    val year: Int,
    val totalCount: Int,
    val totalDuration: Duration,
    val beachRankings: List<BeachRank>,
    val polluterRankings: List<BeachPolluter>,
) : PageViewModel(uri)

data class BeachPolluter(
    val rank: Int,
    val company: String,
    val count: Int,
    val duration: Duration
)

object BeachesPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        beachRankings: () -> List<BeachRank>,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request: Request ->
            val rankings = beachRankings()
            val polluters = rankings.groupBy { it.company }
                .map {
                    BeachPolluter(
                        0,
                        it.key,
                        count = it.value.sumOf { it.count },
                        duration = it.value.map { it.duration }.reduce { acc, duration -> acc + duration }
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
                        year = 2021,
                        totalCount,
                        totalDuration,
                        rankings.sortedByDescending { it.rank },
                        polluterRankings = polluters
                    )
                )
        }
    }
}