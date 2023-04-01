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

class DeltaValue(value: Int) : IntValue(value) {
    companion object : IntValueFactory<DeltaValue>(::DeltaValue)

    fun isPositive() = value > 0
    fun isNegative() = value < 0
}

class RenderableDuration(value: Duration) {
    val hours = value.toHours()
    val days = value.toDays()
    val months = value.toDays() / 30.0
    val years = value.toDays() / 365.0

    val hasMonths = months > 1.0
    val hasYears = months > 12.0
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
                                RenderableConstituency.from(it.constituencyName),
                                mps[it.constituencyName]?.mp
                                    ?: throw Defect("We don't have the MP for ${it.constituencyName}"),
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