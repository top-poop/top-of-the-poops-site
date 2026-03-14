package org.totp.pages

import org.http4k.core.*
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.THE_YEAR
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import org.totp.model.data.SeneddConstituencyName
import org.totp.model.data.Slug
import org.totp.model.data.toSlug

class SeneddConstituenciesPage(
    uri: Uri,
    var year: Int,
    val rankings: List<RenderableSeneddRank>
) : PageViewModel(uri)

data class RenderableSeneddRank(
    val rank: Int,
    val constituency: RenderableSeneddName,
    val count: RenderableCount,
    val duration: RenderableDuration,
    val countDelta: DeltaValue,
    val durationDelta: RenderableDurationDelta
)

data class RenderableSeneddName(
    val name: SeneddConstituencyName,
    val current: Boolean,
    val slug: Slug,
    val uri: Uri,
    val live: Boolean,
)

fun SeneddConstituencyName.toRenderable(current: Boolean = false): RenderableSeneddName {
    val slug = this.toSlug()
    return RenderableSeneddName(
        this,
        current,
        slug,
        uri = Uri.of("/senedd-constituency/$slug"),
        false
    )
}

fun SeneddConstituencyRank.toRenderable(
) = RenderableSeneddRank(
    rank,
    constituencyName.toRenderable(),
    RenderableCount(count),
    duration.toRenderable(),
    countDelta = DeltaValue.of(countDelta),
    durationDelta = RenderableDurationDelta(durationDelta)
)





object SeneddConstituenciesPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        seneddConstituencies: () -> List<SeneddConstituencyRank>,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request: Request ->

            Response(Status.OK)
                .with(
                    viewLens of SeneddConstituenciesPage(
                        pageUriFrom(request),
                        year = THE_YEAR,
                        seneddConstituencies().sortedBy { it.rank }.map {
                            it.toRenderable()
                        }
                    )
                )
        }
    }
}