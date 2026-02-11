package org.totp.pages

import org.http4k.core.*
import org.http4k.lens.Path
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.THE_YEAR
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import org.totp.model.data.GeoJSON
import org.totp.model.data.PlaceName
import org.totp.model.data.Slug
import org.totp.model.data.toSlug


class BadgesLocalitiesPage(
    uri: Uri,
    val year: Int,
    val places: List<RenderablePlaceRank>,
    val boundaries: List<Pair<Slug, GeoJSON>>,
) : PageViewModel(uri)


object BadgesPlacesHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        placeRankings: () -> List<PlaceRank>,
        placeBoundaries: (PlaceName) -> GeoJSON,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        val letter = Path.of("letter")

        return { request ->
            val startLetter = letter(request)
            val rankings = placeRankings().filter {
                it.placeName.value.lowercase().startsWith(startLetter)
            }

            Response(Status.OK)
                .with(
                    viewLens of BadgesLocalitiesPage(
                        pageUriFrom(request),
                        THE_YEAR,
                        rankings.sortedBy { it.placeName }.map {
                            it.toRenderable(false)
                        },
                        rankings.map {
                            it.placeName.toSlug() to placeBoundaries(it.placeName)
                        }
                    )
                )
        }
    }
}