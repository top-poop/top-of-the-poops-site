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


val localityNames = readCSV(
    resource = "/data/localities.csv",
    mapper = { LocalityName(it[0]) }
).toSortedSet(Comparator.comparing { it.value })


val slugToLocality = localityNames.associateBy { it.toSlug() }

class LocalityPage(
    uri: Uri,
    val locality: RenderableLocality,
    val localities: List<RenderableLocality>,
    val share: SocialShare,
    val summary: PollutionSummary,
    val geojson: GeoJSON,
    val csos: List<RenderableCSOTotal>,
    val neighbours: List<RenderableLocality>,
    val rivers: List<RenderableRiverRank>
) :
    PageViewModel(uri)


object LocalityPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        localityTotals: (LocalityName) -> List<CSOTotals>,
        localityBoundary: (LocalityName) -> GeoJSON,
        localityRank: (LocalityName) -> LocalityRank?,
        localityRivers: (LocalityName) -> List<RiverRank>
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        val slug: PathLens<Slug> =
            Path.value(Slug).of("locality", "The locality")


        return { request: Request ->
            val slug = slug(request)

            val localities = slugToLocality.map {
                it.value.toRenderable(
                    current = it.key == slug
                )
            }

            slugToLocality[slug]?.let { localityName ->

                val list = localityTotals(localityName).sortedByDescending { it.duration }

                val summary = list.summary()

                Response(Status.OK)
                    .with(
                        viewLens of LocalityPage(
                            pageUriFrom(request).removeQuery(),
                            locality = localityName.toRenderable(current = true),
                            localities = localities,
                            share = share(summary, localityName, slug, pageUriFrom(request)),
                            summary = summary,
                            geojson = localityBoundary(localityName),
                            list.map {
                                it.toRenderable()
                            },
                            emptyList(),
                            rivers = localityRivers(localityName).take(5).map { it.toRenderable() },
                        )
                    )
            }
                ?: Response(Status.NOT_FOUND)
        }
    }

    private fun share(
        summary: PollutionSummary,
        localityName: LocalityName,
        slug: Slug,
        uri: Uri
    ): SocialShare {

        val numberFormat = NumberFormat.getIntegerInstance()
        val formatted = numberFormat.format(summary.duration.hours)

        return SocialShare(
            uri,
            text = "$localityName had $formatted hours of sewage pollution in ${summary.year}",
            tags = listOf("sewage"),
            via = "sewageuk",
            twitterImageUri = Uri.of("https://top-of-the-poops.org/badges/locality/${slug}-2024.png")
        )
    }
}
