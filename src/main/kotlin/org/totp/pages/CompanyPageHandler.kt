package org.totp.pages

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
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import org.totp.model.data.BathingRank
import org.totp.model.data.CSOTotals
import org.totp.model.data.CompanyName
import org.totp.model.data.CompanySlug
import org.totp.model.data.RenderableCompany
import org.totp.model.data.RiverRank
import org.totp.model.data.WaterCompany
import org.totp.model.data.toSlug
import org.totp.model.data.toRenderable
import java.text.NumberFormat
import java.time.Duration

class CompanyPage(
    uri: Uri,
    val year: Int,
    val csoUri: Uri,
    val name: CompanyName,
    val summary: RenderableCompanyAnnualSummary,
    val company: WaterCompany,
    val links: List<WaterCompanyLink>,
    val rivers: List<RenderableRiverRank>,
    val beaches: List<RenderableBathingRank>,
    val worstCsos: List<RenderableCSOTotal>,
    val share: SocialShare,
    val liveDataUri: Uri?,
) : PageViewModel(uri)


data class CompanyAnnualSummary(
    val name: CompanyName,
    val year: Int,
    val spillCount: Int,
    val duration: Duration,
    val locationCount: Int,
)

data class RenderableCompanyAnnualSummary(
    val company: RenderableCompany,
    val year: Int,
    val count: RenderableCount,
    val duration: RenderableDuration,
    val locationCount: Int,
)

fun CompanyAnnualSummary.toRenderable(): RenderableCompanyAnnualSummary {
    return RenderableCompanyAnnualSummary(
        name.toRenderable(),
        year,
        RenderableCount(spillCount),
        duration.toRenderable(),
        locationCount
    )
}

data class WaterCompanyLink(
    val selected: Boolean,
    val company: WaterCompany,
)

object CompanyPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        companySummaries: () -> List<CompanyAnnualSummary>,
        waterCompanies: () -> List<WaterCompany>,
        riverRankings: () -> List<RiverRank>,
        bathingRankings: () -> List<BathingRank>,
        csoTotals: () -> List<CSOTotals>,
        companyLiveDataAvailable: (CompanyName) -> Uri?
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()
        val companySlug = Path.value(CompanySlug).of("company", "The company")

        val numberFormat = NumberFormat.getIntegerInstance()

        return { request: Request ->

            val slug = companySlug(request)
            val summaries = companySummaries()

            val applicable = summaries.filter { it.name.toSlug() == slug }.sortedByDescending { it.year }

            if (applicable.isNotEmpty()) {

                val companies = waterCompanies()

                val mostRecent = applicable.first().toRenderable()
                val name = mostRecent.company.name

                val company = companies.first { it.name == name }
                val worstCsos = csoTotals()
                    .filter { it.cso.company == name }
                    .sortedByDescending { it.duration }
                    .take(6)
                    .map { it.toRenderable() }

                Response(Status.OK)
                    .with(
                        viewLens of CompanyPage(
                            pageUriFrom(request),
                            year = mostRecent.year,
                            name = name,
                            csoUri = Uri.of("/assets/images/top-of-the-poops-cso-$slug.png"),
                            summary = mostRecent,
                            company = company,
                            links = companies.map {
                                WaterCompanyLink(it.name == name, it)
                            },
                            beaches = bathingRankings().filter { it.company == company.name }.take(6)
                                .map { it.toRenderable() },
                            rivers = riverRankings().filter { it.company == company.name }.take(6)
                                .map { it.toRenderable() },
                            worstCsos = worstCsos,
                            liveDataUri = companyLiveDataAvailable(name),
                            share = SocialShare(
                                pageUriFrom(request),
                                "$name - ${company.handle} - dumped #sewage into rivers,seas & bathing areas ${
                                    numberFormat.format(
                                        mostRecent.count.count
                                    )
                                } times in ${mostRecent.year} - Unacceptable!",
                                cta = "Tweet your displeasure to $name",
                                listOf("sewage"),
                                via = "sewageuk",
                                twitterImageUri = Uri.of("https://top-of-the-poops.org/badges/company/${slug}.png")
                            )
                        )
                    )
            } else {
                Response(Status.NOT_FOUND)
            }
        }
    }
}