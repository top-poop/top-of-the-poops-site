package org.totp.pages

import dev.forkhandles.values.IntValue
import dev.forkhandles.values.IntValueFactory
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
import org.totp.extensions.Defect
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import org.totp.model.data.ConstituencyContact
import java.time.Duration

class ConstituenciesPage(
    uri: Uri,
    var year: Int,
    val constituencyRankings: List<RenderableConstituencyRank>
) : PageViewModel(uri)

interface Delta {
    fun isPositive(): Boolean
    fun isNegative(): Boolean
}

class DeltaValue(value: Int) : IntValue(value), Delta {
    companion object : IntValueFactory<DeltaValue>(::DeltaValue)

    override fun isPositive() = value > 0
    override fun isNegative() = value < 0
}

class RenderableDuration(value: Duration) {
    val hours = value.toHours()
    val days = hours / 24.0
    val months = days / 30.0
    val years = months / 12.0

    val hasMonths = months > 1.0
    val hasYears = years > 1.0
}

class RenderableDurationDelta(val value: Duration): Delta {
    val hours = value.toHours()

    override fun isPositive() = value > Duration.ZERO
    override fun isNegative() = value < Duration.ZERO
}

class RenderableConstituencyRank(
    val rank: Int,
    val constituency: RenderableConstituency,
    val mp: MP,
    val count: RenderableCount,
    val duration: RenderableDuration,
    val countDelta: DeltaValue,
    val durationDelta: RenderableDurationDelta
)

object ConstituenciesPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        constituencyRankings: () -> List<ConstituencyRank>,
        constituencyContacts: () -> List<ConstituencyContact>
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        val mps = constituencyContacts().associateBy { it.constituency }

        return { request: Request ->
            Response(Status.OK)
                .with(
                    viewLens of ConstituenciesPage(
                        pageUriFrom(request),
                        year = 2022,
                        constituencyRankings().sortedBy { it.rank }.map {
                            RenderableConstituencyRank(
                                it.rank,
                                it.constituencyName.toRenderable(),
                                mps[it.constituencyName]?.mp
                                    ?: throw Defect("We don't have the MP for ${it.constituencyName}"),
                                RenderableCount(it.count),
                                RenderableDuration(it.duration),
                                countDelta = DeltaValue.of(it.countDelta),
                                durationDelta = RenderableDurationDelta(it.durationDelta)
                            )
                        },
                    )
                )
        }
    }
}