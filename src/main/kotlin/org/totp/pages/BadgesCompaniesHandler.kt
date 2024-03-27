package org.totp.pages

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.with
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel

class BadgesCompaniesPage(
    uri: Uri,
    val year: Int,
    val companies: List<RenderableCompanyAnnualSummary>
) : PageViewModel(uri)


object BadgesCompaniesHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        companySummaries: () -> List<CompanyAnnualSummary>,
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request ->

            val summaries = companySummaries()

            val applicable = summaries.filter { it.year == 2023 }

            Response(Status.OK)
                .with(
                    viewLens of BadgesCompaniesPage(
                        pageUriFrom(request),
                        2023,
                        companies = applicable.map { it.toRenderable() }
                    ),
                )
        }
    }
}