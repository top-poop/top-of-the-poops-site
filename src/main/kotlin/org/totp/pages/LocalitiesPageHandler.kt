package org.totp.pages

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.http4k.core.*
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.THE_YEAR
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import org.totp.model.TotpHandlebars
import org.totp.model.data.LocalityName
import org.totp.model.data.Slug
import org.totp.model.data.toSlug

class LocalitiesPage(
    uri: Uri,
    var year: Int,
    val localityTableRows: String
) : PageViewModel(uri)

data class RenderableLocality(
    val name: LocalityName,
    val current: Boolean,
    val slug: Slug,
    val uri: Uri,
    val live: Boolean,
)

class RenderableLocalityRank(
    val rank: Int,
    val area: RenderableLocality,
    val count: RenderableCount,
    val duration: RenderableDuration,
    val countDelta: DeltaValue,
    val durationDelta: RenderableDurationDelta,
    val csoCount: RenderableCount,
)

fun tableRows(items: List<RenderableLocalityRank>): String {
    val nf = TotpHandlebars.numberFormat()

    return createHTML().tbody {
        items.map { r ->
            tr {
                td(classes = "align-middle") { +"${r.rank}" }
                td(classes = "align-middle") {
                    a("${r.area.uri}") { +"${r.area.name}" }
                }
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
                td(classes = "align-middle") { +nf(r.csoCount.count) }
            }
        }
    }
}

object LocalitiesPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        areaRankings: () -> List<UrbanAreaRank>,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request: Request ->

            Response(Status.OK)
                .with(
                    viewLens of LocalitiesPage(
                        pageUriFrom(request),
                        year = THE_YEAR,
                        localityTableRows = tableRows(
                            areaRankings().sortedBy { it.rank }.map {
                                it.toRenderable()
                            }
                        )
                    )
                )
        }
    }
}

fun UrbanAreaRank.toRenderable(current: Boolean = false): RenderableLocalityRank {
    val slug = this.localityName.toSlug()
    return RenderableLocalityRank(
        rank,
        RenderableLocality(
            this.localityName,
            current,
            slug,
            uri = Uri.of("/locality/$slug"),
            live = false
        ),
        RenderableCount(count),
        duration.toRenderable(),
        countDelta = DeltaValue.of(countDelta),
        durationDelta = RenderableDurationDelta(durationDelta),
        csoCount = RenderableCount(csoCount)
    )
}
