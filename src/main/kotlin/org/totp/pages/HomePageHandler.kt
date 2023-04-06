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
import org.totp.model.data.BeachRank
import org.totp.model.data.ConstituencyContact
import org.totp.model.data.ConstituencyName
import org.totp.model.data.MediaAppearance
import org.totp.model.data.RiverRank
import org.totp.model.data.WaterCompany
import java.time.Duration
import kotlin.math.floor

data class MP(val name: String, val party: String, val handle: String?, val uri: Uri)

data class ConstituencyRank(
    val rank: Int,
    val constituencyName: ConstituencyName,
    val count: Int,
    val duration: Duration,
    val countDelta: Int,
    val durationDelta: Duration
)

@Suppress("unused")
class HomePage(
    uri: Uri,
    val year: Int,
    val totalCount: Int,
    val totalDuration: Duration,
    val constituencyRankings: List<RenderableConstituencyRank>,
    val companies: List<WaterCompany>,
    val beachRankings: List<RenderableBeachRank>,
    val riverRankings: List<RenderableRiverRank>,
    val appearances: List<MediaAppearance>,
    val share: SocialShare
) : PageViewModel(uri)

object HomepageHandler {

    operator fun invoke(
        renderer: TemplateRenderer,
        constituencyRankings: () -> List<ConstituencyRank>,
        beachRankings: () -> List<BeachRank>,
        riverRankings: () -> List<RiverRank>,
        appearances: () -> List<MediaAppearance>,
        companies: () -> List<WaterCompany>,
        mpFor: (ConstituencyName) -> MP,
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
                        year = 2022,
                        totalSpillsRounded,
                        totalDuration,
                        rankings.take(10).map { it.toRenderable(mpFor)},
                        companies(),
                        beachRankings().take(10).map { it.toRenderable() },
                        rivers.map {
                            it.toRenderable()
                        },
                        appearances().sortedByDescending { it.date }.take(8),
                        SocialShare(
                            pageUriFrom(request),
                            "Water companies are dumping #sewage into rivers and bathing areas all over the UK - over ${totalSpillsRounded} times in 2022 - it needs to be stopped",
                            cta = "Take action. Tweet this to your followers",
                            listOf("sewage"),
                            via = "sewageuk",
                            twitterImageUri=Uri.of("https://top-of-the-poops.org/badges/home/home.png")
                        )
                    )
                )
        }
    }
}

