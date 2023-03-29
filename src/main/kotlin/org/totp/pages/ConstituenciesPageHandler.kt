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
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import java.time.Duration

class ConstituenciesPage(
    uri: Uri,
    var year: Int,
    val constituencyRankings: List<RenderableConstituencyRank>
) : PageViewModel(uri)

class DeltaValue(value: Int) : IntValue(value) {
    companion object : IntValueFactory<DeltaValue>(::DeltaValue)

    fun isPositive() = value > 0
    fun isNegative() = value < 0
}

class RenderableDuration(value: Duration) {
    val hours = value.toHours()
}

class RenderableDurationDelta(val value: Duration) {
    val hours = value.toHours()

    fun isPositive() = value > Duration.ZERO
    fun isNegative() = value < Duration.ZERO

}

class RenderableConstituencyRank(
    val rank: Int,
    val constituency: RenderableConstituency,
    val mp: MP,
    val count: Int,
    val duration: RenderableDuration,
    val countDelta: DeltaValue,
    val durationDelta: RenderableDurationDelta
)

object ConstituenciesPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        consituencyRankings: () -> List<ConstituencyRank>,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request: Request ->
            Response(Status.OK)
                .with(
                    viewLens of ConstituenciesPage(
                        pageUriFrom(request),
                        year = 2021,
                        consituencyRankings().sortedBy { it.rank }.map {
                            RenderableConstituencyRank(
                                it.rank,
                                RenderableConstituency.from(it.constituencyName),
                                it.mp,
                                it.count,
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