package org.totp.pages

import dev.forkhandles.values.IntValue
import dev.forkhandles.values.IntValueFactory
import kotlinx.html.a
import kotlinx.html.classes
import kotlinx.html.stream.createHTML
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.tr
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
import org.totp.model.TotpHandlebars
import org.totp.model.data.ConstituencyName
import java.time.Duration

class ConstituenciesPage(
    uri: Uri,
    var year: Int,
    val constituenciesTableRows: String
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

fun Duration.toRenderable() = RenderableDuration(this)

class RenderableDurationDelta(val value: Duration) : Delta {
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

/*
                        {{#each constituencyRankings}}
                            <tr>
                                <td class="align-middle">{{this.rank}}</td>
                                <td class="align-middle"><a
                                        href="{{this.constituency.uri}}">{{this.constituency.name}}</a></td>
                                <td class="align-middle"><a href="{{this.mp.uri}}">{{this.mp.name}}</a></td>
                                <td class="align-middle">{{this.mp.party}}</td>
                                <td class="align-middle">{{numberFormat this.count.count}}</td>
                                <td class="align-middle {{>components/class-delta this.countDelta}}">{{numberFormat
                                        this.countDelta.value}}</td>
                                <td class="align-middle">{{numberFormat this.duration.hours}}</td>
                                <td class="align-middle {{>components/class-delta this.durationDelta}}">{{numberFormat
                                        this.durationDelta.hours}}</td>
                            </tr>
                        {{/each}}
 */


fun tableRows(items: List<RenderableConstituencyRank>): String {
    val nf = TotpHandlebars.numberFormat()

    return createHTML().tbody {
        items.map { r ->
            tr {
                td(classes = "align-middle") { +"${r.rank}" }
                td(classes = "align-middle") {
                    a("${r.constituency.uri}") { +"${r.constituency.name}" }
                }
                td(classes = "align-middle") {
                    a("${r.mp.uri}") { +r.mp.name }
                }
                td(classes = "align-middle") { +r.mp.party }
                td(classes = "align-middle") { +nf(r.count.count) }
                td(classes = "align-middle") {
                    classes += classesFor(r.countDelta)
                    +nf(r.countDelta.value)
                }
                td(classes = "align-middle") { +nf(r.duration.hours) }
                td(classes = "align-middle") {
                    classes += classesFor(r.durationDelta)
                    +nf(r.durationDelta.hours)
                }
            }
        }
    }
}

object ConstituenciesPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        constituencyRankings: () -> List<ConstituencyRank>,
        mpFor: (ConstituencyName) -> MP
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request: Request ->

            Response(Status.OK)
                .with(
                    viewLens of ConstituenciesPage(
                        pageUriFrom(request),
                        year = 2022,
                        tableRows(
                            constituencyRankings().sortedBy { it.rank }.map {
                                it.toRenderable(mpFor)
                            }
                        )
                    )
                )
        }
    }
}

fun ConstituencyRank.toRenderable(
    mp: (ConstituencyName) -> MP
) = RenderableConstituencyRank(
    rank,
    constituencyName.toRenderable(),
    mp(constituencyName),
    RenderableCount(count),
    duration.toRenderable(),
    countDelta = DeltaValue.of(countDelta),
    durationDelta = RenderableDurationDelta(durationDelta)
)
