package org.totp.pages

import org.http4k.core.*
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.THE_YEAR
import org.totp.db.StreamData
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import org.totp.model.data.*
import java.time.Duration
import kotlin.math.floor

data class MP(val name: String, val party: String, val handle: String?, val uri: Uri)

data class ConstituencyRank(
    val rank: Int,
    val constituencyName: ConstituencyName,
    val count: Int,
    val duration: Duration,
    val countDelta: Int,
    val durationDelta: Duration,
)

data class LocalityRank(
    val rank: Int,
    val localityName: LocalityName,
    val overflowCount: Int,
    val zeroMonitoringCount: Int,
    val duration: Duration,
    val countDelta: Int,
    val durationDelta: Duration,
    val csoCount: Int
)

data class RenderableShellfishRank(
    val rank: Int,
    val shellfish: RenderableShellfishName,
    val company: RenderableCompany,
    val count: RenderableCount,
    val duration: RenderableDuration,
    val countDelta: DeltaValue,
    val durationDelta: RenderableDurationDelta,
)

fun ShellfishRank.toRenderable(): RenderableShellfishRank {
    return RenderableShellfishRank(
        rank,
        shellfishery.toRenderable(),
        company.toRenderable(),
        RenderableCount(count),
        duration.toRenderable(),
        countDelta,
        RenderableDurationDelta(durationDelta)
    )
}


@Suppress("unused")
class HomePage(
    uri: Uri,
    val year: Int,
    val totalCount: Int,
    val totalDuration: RenderableDuration,
    val constituencyRankings: List<RenderableConstituencyRank>,
    val companies: List<WaterCompany>,
    val beachRankings: List<RenderableBathingRank>,
    val riverRankings: List<RenderableRiverRank>,
    val shellfishRankings: List<RenderableShellfishRank>,
    val appearances: List<MediaAppearance>,
    val share: SocialShare,
    val summary: StreamData.StreamOverflowSummary,
) : PageViewModel(uri)

object HomepageHandler {

    operator fun invoke(
        renderer: TemplateRenderer,
        constituencyRankings: () -> List<ConstituencyRank>,
        bathingRankings: () -> List<BathingRank>,
        riverRankings: () -> List<RiverRank>,
        shellfishRankings: () -> List<ShellfishRank>,
        appearances: () -> List<MediaAppearance>,
        companies: () -> List<WaterCompany>,
        mpFor: (ConstituencyName) -> MP,
        streamSummary: () -> StreamData.StreamOverflowSummary,
    ): HttpHandler {

        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request ->

            val rivers = riverRankings().take(10)
            val rankings = constituencyRankings()

            val totalSpills = rankings.sumOf { it.count }
            val totalSpillsRounded = (floor(totalSpills / 1000.0) * 1000).toInt()

            val totalDuration = rankings.map { it.duration }.reduce { acc, duration -> acc + duration }

            Response(Status.OK)
                .with(
                    viewLens of HomePage(
                        pageUriFrom(request),
                        year = THE_YEAR,
                        totalSpillsRounded,
                        totalDuration.toRenderable(),
                        rankings.take(10).map { it.toRenderable(mpFor) },
                        companies(),
                        bathingRankings().take(10).map { it.toRenderable() },
                        rivers.map {
                            it.toRenderable()
                        },
                        shellfishRankings().take(10).map { it.toRenderable() },
                        appearances().sortedByDescending { it.date },
                        SocialShare(
                            pageUriFrom(request),
                            "Water companies are dumping #sewage into rivers and bathing areas all over the UK - over ${totalSpillsRounded} times in 2024 - it needs to be stopped",
                            listOf("sewage"),
                            via = "sewageuk",
                            twitterImageUri = Uri.of("https://top-of-the-poops.org/badges/home/home-2024.png")
                        ),
                        summary = streamSummary(),
                    )
                )
        }
    }
}

