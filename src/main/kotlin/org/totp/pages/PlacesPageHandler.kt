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
import org.totp.model.data.PlaceName
import org.totp.model.data.Slug
import org.totp.model.data.toSlug
import java.time.Duration

class PlacesPage(
    uri: Uri,
    var year: Int,
    val placeTableRows: String
) : PageViewModel(uri)

data class RenderablePlace(
    val name: PlaceName,
    val current: Boolean,
    val slug: Slug,
    val uri: Uri,
    val live: Boolean,
)

class RenderablePlaceRank(
    val rank: Int,
    val place: RenderablePlace,
    val overflowCount: RenderableCount,
    val zeroMonitoringCount: Int,
    val duration: RenderableDuration,
    val countDelta: DeltaValue,
    val durationDelta: RenderableDurationDelta,
    val csoCount: RenderableCount,
) {

    fun variant(): String {
        if (csoCount.count == 0) {
            return "none"
        }
        if (zeroMonitoringCount == csoCount.count) {
            return "unmonitored"
        }

        if (zeroMonitoringCount > 0) {
            return "unsure"
        }
        if (duration.value == Duration.ZERO) {
            return "zero"
        }
        return "normal";
    }

    fun unmonitored(): Boolean = zeroMonitoringCount > 0
    fun sewage(): Boolean = duration.value > Duration.ZERO
}

fun tableRows(items: List<RenderablePlaceRank>): String {
    val nf = TotpHandlebars.numberFormat()

    return createHTML().tbody {
        items.map { r ->
            tr {
                td(classes = "align-middle") { +"${r.rank}" }
                td(classes = "align-middle") {
                    a("${r.place.uri}") { +"${r.place.name}" }
                }
                td(classes = "align-middle") { +nf(r.overflowCount.count) }
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

object PlacesPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        areaRankings: () -> List<PlaceRank>,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request: Request ->

            Response(Status.OK)
                .with(
                    viewLens of PlacesPage(
                        pageUriFrom(request),
                        year = THE_YEAR,
                        placeTableRows = tableRows(
                            areaRankings().sortedBy { it.rank }.map {
                                it.toRenderable()
                            }
                        )
                    )
                )
        }
    }
}

fun PlaceRank.toRenderable(current: Boolean = false): RenderablePlaceRank {
    val name = this.placeName
    return RenderablePlaceRank(
        rank,
        place = name.toRenderable(current),
        overflowCount = RenderableCount(overflowCount),
        zeroMonitoringCount = zeroMonitoringCount,
        duration = duration.toRenderable(),
        countDelta = DeltaValue.of(countDelta),
        durationDelta = RenderableDurationDelta(durationDelta),
        csoCount = RenderableCount(csoCount)
    )
}

fun PlaceName.toRenderable(current: Boolean = false): RenderablePlace {
    val slug = toSlug()

    return RenderablePlace(
        this,
        current,
        slug,
        uri = Uri.of("/place/$slug"),
        live = false
    )
}
