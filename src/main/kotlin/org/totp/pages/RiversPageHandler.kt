package org.totp.pages

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
import org.http4k.core.query
import org.http4k.core.with
import org.http4k.lens.Query
import org.http4k.lens.boolean
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import org.totp.model.TotpHandlebars.numberFormat
import org.totp.model.data.CompanyName
import org.totp.model.data.RiverRank
import org.totp.model.data.WaterwayName
import java.time.Duration

class RiversPage(
    uri: Uri,
    val year: Int,
    val totalCount: Int,
    val totalDuration: Duration,
    val showingSummary: Boolean,
    val showAllUri: Uri,
    val riverRankings: List<RenderableRiverRank>,
    val riverTableRows: String,
) : PageViewModel(uri)


data class RenderableWaterway(val name: WaterwayName, val slug: WaterwaySlug, val uri: Uri)

data class RenderableCount(val count: Int) {

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
    return RenderableRiverRank(
        rank,
        river.toRenderable(company),
        RenderableCompany.from(company),
        RenderableCount(count),
        RenderableDuration(duration),
        countDelta,
        RenderableDurationDelta(durationDelta)
    )
}

fun WaterwayName.toRenderable(companyName: CompanyName): RenderableWaterway {
    val waterwaySlug = WaterwaySlug.from(this)
    val companySlug = CompanySlug.from(companyName)
    return RenderableWaterway(this, waterwaySlug, Uri.of("/waterway/$companySlug/$waterwaySlug"))
}


/*
    <tr>
        <td>{{this.rank}}</td>
        <td><a href="{{this.river.uri}}">{{this.river.name}}</a></td>
        <td><a href="{{this.company.uri}}">{{this.company.name}}</a></td>
        <td>{{numberFormat this.count.count}}</td>
        <td class="align-middle {{#if this.countDelta.positive}}delta-positive {{else}}delta-negative {{/if}}">{{numberFormat this.countDelta.value}}</td>
        <td>{{numberFormat this.duration.hours}}</td>
        <td class="align-middle {{#if this.countDelta.positive}}delta-positive {{else}}delta-negative {{/if}}">{{numberFormat this.durationDelta.hours}}</td>
    </tr>
 */

fun classesFor(d: Delta): Set<String> {
    if (d.isNegative()) {
        return setOf("delta-negative")
    } else if (d.isPositive()) {
        return setOf("delta-positive")
    }
    return setOf()
}


// Handlebars too slow for this big list.
fun tableRows(items: List<RenderableRiverRank>): String {

    val nf = numberFormat()

    return createHTML().tbody {
        items.map { r ->
            tr {
                td { +"${r.rank}" }
                td { a("${r.river.uri}") { +"${r.river.name}" } }
                td { a("${r.company.uri}") { +"${r.company.name}" } }
                td { +nf(r.count.count) }
                td(classes = "align-middle") {
                    classes += classesFor(r.countDelta)
                    +nf(r.countDelta.value)
                }
                td { +nf(r.duration.hours) }
                td(classes = "align-middle") {
                    classes += classesFor(r.durationDelta)
                    +nf(r.durationDelta.hours)
                }
            }
        }
    }
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

            val renderables = display.map {
                it.toRenderable()
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
                        renderables,
                        tableRows(renderables)
                    )
                )
        }
    }
}