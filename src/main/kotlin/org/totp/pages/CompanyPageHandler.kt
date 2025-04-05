package org.totp.pages

import org.http4k.core.*
import org.http4k.lens.Path
import org.http4k.lens.value
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.db.StreamData.StreamCSOLiveOverflow
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import org.totp.model.data.*
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant

class CSOLiveData(
    val overflowing: List<StreamCSOLiveOverflow>
)

data class RenderableCSOLiveOverflow(
    val started: Instant,
    val constituency: RenderableConstituency,
    val id: String,
    val company: CompanyName,
)

class RenderableCSOLiveData(
    val overflowing: List<RenderableCSOLiveOverflow>
) {
    companion object {
        fun from(it: CSOLiveData): RenderableCSOLiveData {
            return RenderableCSOLiveData(
                overflowing = it.overflowing.map {
                    RenderableCSOLiveOverflow(
                        started = it.started,
                        constituency = it.pcon24nm.toRenderable(),
                        id = it.id.value,
                        company = it.company
                    )
                }
            )
        }
    }
}

class CompanyPage(
    uri: Uri,
    val year: Int,
    val csoUri: Uri,
    val company: RenderableCompany,
    val summary: RenderableCompanyAnnualSummary,
    val info: WaterCompany,
    val links: List<WaterCompanyLink>,
    val rivers: List<RenderableRiverRank>,
    val beaches: List<RenderableBathingRank>,
    val worstCsos: List<RenderableCSOTotal>,
    val share: SocialShare,
    val live: RenderableCSOLiveData?,
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
        companyLivedata: (CompanyName) -> CSOLiveData?
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
                val companyCsos = csoTotals().filter { it.cso.company == name }

                val worstCsos = companyCsos
                    .sortedByDescending { it.duration }
                    .take(6)
                    .map { it.toRenderable() }

                Response(Status.OK)
                    .with(
                        viewLens of CompanyPage(
                            pageUriFrom(request),
                            year = mostRecent.year,
                            company = name.toRenderable(),
                            csoUri = Uri.of("/assets/images/top-of-the-poops-cso-$slug.png"),
                            summary = mostRecent,
                            info = company,
                            links = companies.map {
                                WaterCompanyLink(it.name == name, it)
                            },
                            beaches = bathingRankings().filter { it.company == company.name }.take(6)
                                .map { it.toRenderable() },
                            rivers = riverRankings().filter { it.company == company.name }.take(6)
                                .map { it.toRenderable() },
                            worstCsos = worstCsos,
                            live = companyLivedata(name)?.let { RenderableCSOLiveData.from(it) },
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
                                twitterImageUri = Uri.of("https://top-of-the-poops.org/badges/company/${slug}-2024.png")
                            )
                        )
                    )
            } else {
                Response(Status.NOT_FOUND)
            }
        }
    }
}