package org.totp.pages

import dev.forkhandles.values.StringValue
import dev.forkhandles.values.StringValueFactory
import org.http4k.core.*
import org.http4k.lens.Path
import org.http4k.lens.value
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.extensions.kebabCase
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import org.totp.model.data.*
import java.text.NumberFormat


class WaterwayPage(
    uri: Uri,
    val name: WaterwayName,
    val share: SocialShare,
    val summary: PollutionSummary,
    val constituencies: List<RenderableConstituencyRank>,
    val localities: List<RenderableLocalityRank>,
    val csos: List<RenderableCSOTotal>,
) :
    PageViewModel(uri)


class WaterwaySlug(value: String) : StringValue(value) {
    companion object : StringValueFactory<WaterwaySlug>(::WaterwaySlug) {
        fun from(name: WaterwayName): WaterwaySlug {
            return of(name.value.kebabCase())
        }
    }
}

object WaterwayPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        waterwaySpills: (WaterwaySlug, Slug) -> List<CSOTotals>,
        mpFor: (ConstituencyName) -> MP,
        constituencyRank: (ConstituencyName) -> ConstituencyRank?,
        localityRank: (LocalityName) -> LocalityRank?,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        val waterwaySlug = Path.value(WaterwaySlug).of("waterway", "The waterway")
        val slug = Path.value(Slug).of("company", "The company")

        return { request: Request ->
            val numberFormat = NumberFormat.getIntegerInstance()

            val spills = waterwaySpills(waterwaySlug(request), slug(request))

            if (spills.isEmpty()) {
                Response(Status.NOT_FOUND)
            } else {

                val name = spills.first().cso.waterway

                val constituencies = spills
                    .map { it.constituency }
                    .toSet()
                    .sorted()
                    .mapNotNull { constituencyRank(it) }
                    .map { it.toRenderable(mpFor) }

                val localities = spills.flatMap { it.localities }
                    .toSet()
                    .sorted()
                    .mapNotNull { localityRank(it) }
                    .map { it.toRenderable() }

                val summary = spills.summary()

                Response(Status.OK)
                    .with(
                        viewLens of WaterwayPage(
                            pageUriFrom(request),
                            name,
                            SocialShare(
                                pageUriFrom(request),
                                text = "$name had ${numberFormat.format(summary.count.count)} sewage overflows in ${summary.year}",
                                tags = listOf("sewage"),
                                via = "sewageuk"
                            ),
                            summary = summary,
                            constituencies = constituencies,
                            localities = localities,
                            csos = spills
                                .sortedByDescending { it.duration }
                                .map {
                                    it.toRenderable()
                                })
                    )
            }
        }
    }
}