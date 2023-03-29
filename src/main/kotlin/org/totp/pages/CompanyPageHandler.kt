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
import org.totp.model.data.CompanyName
import org.totp.model.data.WaterCompany
import java.text.NumberFormat
import java.time.Duration

class CompanyPage(
    uri: Uri,
    val year: Int,
    val csoUri: Uri,
    val name: CompanyName,
    val summary: CompanyAnnualSummary,
    val company: WaterCompany,
    val links: List<WaterCompanyLink>,
    val share: SocialShare,
) : PageViewModel(uri)

class CompanySlug(value: String) : StringValue(value) {
    companion object : StringValueFactory<CompanySlug>(::CompanySlug) {
        fun from(name: CompanyName): CompanySlug {
            return of(name.value.kebabCase())
        }
    }
}

data class CompanyAnnualSummary(
    val name: CompanyName,
    val year: Int,
    val spillCount: Int,
    val duration: Duration,
    val locationCount: Int
)

data class WaterCompanyLink(
    val selected: Boolean,
    val company: WaterCompany
)

object CompanyPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        companySummaries: () -> List<CompanyAnnualSummary>,
        waterCompanies: () -> List<WaterCompany>
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()
        val companySlug = Path.value(CompanySlug).of("company", "The company")

        val numberFormat = NumberFormat.getIntegerInstance()

        return { request: Request ->

            val slug = companySlug(request)
            val summaries = companySummaries()

            val applicable = summaries.filter { CompanySlug.from(it.name) == slug }.sortedByDescending { it.year }

            if (applicable.isNotEmpty()) {

                val companies = waterCompanies()

                val mostRecent = applicable.first()
                val name = mostRecent.name

                val company = companies.first { it.name == name }
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
                            share = SocialShare(
                                pageUriFrom(request),
                                "$name - ${company.handle} - dumped #sewage into rivers,seas & bathing areas ${
                                    numberFormat.format(
                                        mostRecent.spillCount
                                    )
                                } times in ${mostRecent.year} - Take action!",
                                cta = "Tweet your displeasure to $name",
                                listOf("sewage"),
                                via = "sewageuk"
                            )
                        )
                    )
            } else {
                Response(Status.NOT_FOUND)
            }
        }
    }
}