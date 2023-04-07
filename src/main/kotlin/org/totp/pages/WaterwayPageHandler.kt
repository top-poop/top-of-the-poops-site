package org.totp.pages

import dev.forkhandles.values.StringValue
import dev.forkhandles.values.StringValueFactory
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.with
import org.http4k.lens.Path
import org.http4k.lens.value
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.extensions.kebabCase
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import org.totp.model.data.CSOTotals
import org.totp.model.data.ConstituencyName
import org.totp.model.data.WaterwayName
import java.text.NumberFormat


class WaterwayPage(
    uri: Uri,
    val name: WaterwayName,
    val share: SocialShare,
    val summary: PollutionSummary,
    val constituencies: List<RenderableConstituencyRank>,
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
        waterwaySpills: (WaterwaySlug, CompanySlug) -> List<CSOTotals>,
        mpFor: (ConstituencyName) -> MP,
        constituencyRank: (ConstituencyName) -> ConstituencyRank?
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        val waterwaySlug = Path.value(WaterwaySlug).of("waterway", "The waterway")
        val companySlug = Path.value(CompanySlug).of("company", "The company")

        val numberFormat = NumberFormat.getIntegerInstance()

        return { request: Request ->

            val spills = waterwaySpills(waterwaySlug(request), companySlug(request))

            if (spills.isEmpty()) {
                Response(Status.NOT_FOUND)
            } else {

                val name = spills.first().cso.waterway

                val constituencies = spills
                    .map { it.constituency }
                    .toSet()
                    .sorted()
                    .map {
                        constituencyRank(it)
                    }
                    .filterNotNull()
                    .map {
                        it.toRenderable(mpFor)
                    }

                val summary = PollutionSummary.from(spills)

                Response(Status.OK)
                    .with(
                        viewLens of WaterwayPage(
                            pageUriFrom(request),
                            name,
                            SocialShare(
                                pageUriFrom(request),
                                text = "$name had ${numberFormat.format(summary.count.count)} sewage overflows in ${summary.year}",
                                cta = "$name pollution",
                                tags = listOf("sewage"),
                                via = "sewageuk"
                            ),
                            summary = summary,
                            constituencies = constituencies,
                            csos = spills
                                .sortedByDescending { it.duration }
                                .map {
                                    RenderableCSOTotal(
                                        it.constituency.toRenderable(),
                                        it.cso.let {
                                            RenderableCSO(
                                                RenderableCompany.from(it.company),
                                                it.sitename,
                                                it.waterway.toRenderable(it.company),
                                                it.location
                                            )
                                        },
                                        it.count,
                                        it.duration,
                                        it.reporting
                                    )
                                })
                    )
            }
        }
    }
}