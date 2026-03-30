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
import java.text.NumberFormat
import java.time.Clock

class SeneddConstituencyPage(
    uri: Uri,
    val constituency: RenderableSeneddName,
    val share: SocialShare,
    val summary: PollutionSummary,
    val geojson: GeoJSON,
    val csos: List<RenderableCSOTotal>,
    val constituencies: List<RenderableSeneddName>,
    val neighbours: List<RenderableSeneddRank>,
    val westminsters: List<RenderableConstituencyRank>,
) : PageViewModel(uri)

object SeneddConstituencyPageHandler {
    operator fun invoke(
        clock: Clock,
        renderer: TemplateRenderer,
        constituencySpills: (SeneddConstituencyName) -> List<CSOTotals>,
        constituencyBoundary: (SeneddConstituencyName) -> GeoJSON,
        constituencyNeighbours: (SeneddConstituencyName) -> List<SeneddConstituencyName>,
        constituencyRank: (SeneddConstituencyName) -> SeneddConstituencyRank?,
        mpFor: (ConstituencyName) -> MP?,
        westminsterConstituencyRank: (ConstituencyName) -> ConstituencyRank?,
        westminsterConstituencies: (SeneddConstituencyName) -> List<ConstituencyName>
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        val slug: PathLens<Slug> =
            Path.value(Slug).of("constituency", "The constituency")

        return { request: Request ->
            val slug = slug(request)

            slugToSeneddConstituency[slug]?.let { constituencyName ->

                val otherSeneddConstituencies = slugToSeneddConstituency
                    .map {
                        it.value.toRenderable(
                            current = it.key == slug,
                        )
                    }

                val spills = constituencySpills(constituencyName).sortedByDescending { it.duration }

                val neighbours = constituencyNeighbours(constituencyName)
                    .sorted()
                    .mapNotNull { constituencyRank(it) }
                    .map { it.toRenderable() }

                val summary = spills.summary()

                Response(Status.OK)
                    .with(
                        viewLens of SeneddConstituencyPage(
                            pageUriFrom(request).removeQuery(),
                            constituencyName.toRenderable(current = true),
                            share = share(summary, constituencyName, slug, pageUriFrom(request)),
                            summary = summary,
                            geojson = constituencyBoundary(constituencyName),
                            csos = spills.map {
                                it.toRenderable()
                            },
                            constituencies = otherSeneddConstituencies,
                            neighbours = neighbours,
                            westminsters = westminsterConstituencies(constituencyName)
                                .mapNotNull ( westminsterConstituencyRank )
                                .map { it.toRenderable(mpFor) }
                        )
                    )
            }
                ?: Response(Status.NOT_FOUND)
        }
    }

    private fun share(
        summary: PollutionSummary,
        constituencyName: SeneddConstituencyName,
        slug: Slug,
        uri: Uri
    ): SocialShare {

        val numberFormat = NumberFormat.getIntegerInstance()
        val formatted = numberFormat.format(summary.duration.hours)

        return SocialShare(
            uri,
            text = "$constituencyName had $formatted hours of sewage pollution in ${summary.year}",
            tags = listOf("sewage"),
            via = "sewageuk",
            twitterImageUri = Uri.of("https://top-of-the-poops.org/badges/senedd-constituency/${slug}-2025.png")
        )
    }
}
