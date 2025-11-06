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
import java.time.Duration

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
    val locality: RenderableLocality,
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

fun tableRows(items: List<RenderableLocalityRank>): String {
    val nf = TotpHandlebars.numberFormat()

    return createHTML().tbody {
        items.map { r ->
            tr {
                td(classes = "align-middle") { +"${r.rank}" }
                td(classes = "align-middle") {
                    a("${r.locality.uri}") { +"${r.locality.name}" }
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

object LocalitiesPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        areaRankings: () -> List<LocalityRank>,
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

fun LocalityRank.toRenderable(current: Boolean = false): RenderableLocalityRank {
    val name = this.localityName
    return RenderableLocalityRank(
        rank,
        locality = name.toRenderable(current),
        overflowCount = RenderableCount(overflowCount),
        zeroMonitoringCount = zeroMonitoringCount,
        duration = duration.toRenderable(),
        countDelta = DeltaValue.of(countDelta),
        durationDelta = RenderableDurationDelta(durationDelta),
        csoCount = RenderableCount(csoCount)
    )
}

fun LocalityName.toRenderable(current: Boolean): RenderableLocality {
    val slug = toSlug()

    return RenderableLocality(
        this,
        current,
        slug,
        uri = Uri.of("/locality/$slug"),
        live = false
    )
}
