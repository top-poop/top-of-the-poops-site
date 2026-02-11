package org.totp.pages

import org.http4k.core.*
import org.http4k.lens.Path
import org.http4k.lens.PathLens
import org.http4k.lens.value
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.http4k.pageUriFrom
import org.totp.http4k.removeQuery
import org.totp.model.PageViewModel
import org.totp.model.data.*
import org.totp.text.csv.readCSV
import java.text.NumberFormat
import kotlin.collections.take


val placeNames = readCSV(
    resource = "/data/localities.csv",
    mapper = { PlaceName(it[0]) }
).toSortedSet(Comparator.comparing { it.value })


val slugToPlace = placeNames.associateBy { it.toSlug() }

class PlacePage(
    uri: Uri,
    val place: RenderablePlace,
    val places: List<RenderablePlace>,
    val share: SocialShare,
    val summary: PollutionSummary,
    val geojson: GeoJSON,
    val csos: List<RenderableCSOTotal>,
    val neighbours: List<RenderablePlace>,
    val rivers: List<RenderableRiverRank>
) :
    PageViewModel(uri)


object PlacePageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        placeTotals: (PlaceName) -> List<CSOTotals>,
        placeBoundary: (PlaceName) -> GeoJSON,
        placeRank: (PlaceName) -> PlaceRank?,
        placeRivers: (PlaceName) -> List<RiverRank>
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        val slug: PathLens<Slug> =
            Path.value(Slug).of("place", "The place")


        return { request: Request ->
            val slug = slug(request)

            val places = slugToPlace.map {
                it.value.toRenderable(
                    current = it.key == slug
                )
            }

            slugToPlace[slug]?.let { placeName ->

                val list = placeTotals(placeName).sortedByDescending { it.duration }

                val summary = list.summary()

                Response(Status.OK)
                    .with(
                        viewLens of PlacePage(
                            pageUriFrom(request).removeQuery(),
                            place = placeName.toRenderable(current = true),
                            places = places,
                            share = share(summary, placeName, slug, pageUriFrom(request)),
                            summary = summary,
                            geojson = placeBoundary(placeName),
                            list.map {
                                it.toRenderable()
                            },
                            emptyList(),
                            rivers = placeRivers(placeName).take(5).map { it.toRenderable() },
                        )
                    )
            }
                ?: Response(Status.NOT_FOUND)
        }
    }

    private fun share(
        summary: PollutionSummary,
        placeName: PlaceName,
        slug: Slug,
        uri: Uri
    ): SocialShare {

        val numberFormat = NumberFormat.getIntegerInstance()
        val formatted = numberFormat.format(summary.duration.hours)

        return SocialShare(
            uri,
            text = "$placeName had $formatted hours of sewage pollution in ${summary.year}",
            tags = listOf("sewage"),
            via = "sewageuk",
            twitterImageUri = Uri.of("https://top-of-the-poops.org/badges/locality/${slug}-2024.png")
        )
    }
}
