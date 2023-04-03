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
import org.totp.model.data.CompanyName
import java.text.NumberFormat
import java.time.Duration

class BeachesPage(
    uri: Uri,
    val year: Int,
    val totalCount: Int,
    val totalDuration: Duration,
    val beachRankings: List<RenderableBeachRank>,
    val polluterRankings: List<BeachPolluter>,
    val share: SocialShare,
) : PageViewModel(uri)

data class RenderableCompany(val name: CompanyName, val slug: CompanySlug, val uri: Uri) {
    companion object {
        fun from(companyName: CompanyName): RenderableCompany {
            val slug = CompanySlug.from(companyName)
            return RenderableCompany(companyName, slug, slug.let { Uri.of("/company/$it") })
        }
    }
}

data class BeachPolluter(
    val rank: Int,
    val company: RenderableCompany,
    val count: Int,
    val duration: Duration,
    val countDelta: DeltaValue,
    val durationDelta: RenderableDurationDelta
)

data class RenderableBeachRank(
    val rank: Int,
    val beach: String,
    val company: RenderableCompany,
    val count: RenderableCount,
    val duration: RenderableDuration,
    val countDelta: DeltaValue,
    val durationDelta: RenderableDurationDelta
)

fun BeachRank.toRenderable(): RenderableBeachRank {
    return RenderableBeachRank(
        rank,
        beach,
        RenderableCompany.from(company),
        RenderableCount(count),
        RenderableDuration(duration),
        countDelta,
        RenderableDurationDelta(durationDelta)
    )
}


object BeachesPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        beachRankings: () -> List<BeachRank>,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request: Request ->
            val numberFormat = NumberFormat.getNumberInstance()
            val rankings = beachRankings().sortedBy { it.rank }
            val polluters = rankings.groupBy { it.company }
                .map {
                    BeachPolluter(
                        0,
                        RenderableCompany.from(it.key),
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
                        year = 2022,
                        totalCount,
                        totalDuration,
                        rankings.map {
                            it.toRenderable()
                        },
                        polluterRankings = polluters,
                        SocialShare(
                            pageUriFrom(request),
                            "Are the beaches safe for swimming? - ${numberFormat.format(totalCount)} sewage pollution incidents in 2022",
                            cta = "Take action. Tweet this to your followers",
                            listOf("sewage"),
                            via = "sewageuk",
                            twitterImageUri=Uri.of("https://top-of-the-poops.org/badges/home/beaches.png")
                        )

                    )
                )
        }
    }
}