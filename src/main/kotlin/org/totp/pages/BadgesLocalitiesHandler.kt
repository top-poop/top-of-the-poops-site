package org.totp.pages

import org.http4k.core.*
import org.http4k.lens.Path
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.THE_YEAR
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import org.totp.model.data.GeoJSON
import org.totp.model.data.LocalityName
import org.totp.model.data.Slug
import org.totp.model.data.toSlug


class BadgesLocalitiesPage(
    uri: Uri,
    val year: Int,
    val localities: List<RenderableLocalityRank>,
    val boundaries: List<Pair<Slug, GeoJSON>>,
) : PageViewModel(uri)


object BadgesLocalitiesHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        localityRankings: () -> List<LocalityRank>,
        localityBoundaries: (LocalityName) -> GeoJSON,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        val letter = Path.of("letter")

        return { request ->
            val startLetter = letter(request)
            val rankings = localityRankings().filter {
                it.localityName.value.lowercase().startsWith(startLetter)
            }

            Response.Companion(Status.OK)
                .with(
                    viewLens of BadgesLocalitiesPage(
                        pageUriFrom(request),
                        THE_YEAR,
                        rankings.sortedBy { it.localityName }.map {
                            it.toRenderable(false)
                        },
                        rankings.map {
                            it.localityName.toSlug() to localityBoundaries(it.localityName)
                        }
                    )
                )
        }
    }
}