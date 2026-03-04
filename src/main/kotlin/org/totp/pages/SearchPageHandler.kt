package org.totp.pages

import org.http4k.core.*
import org.http4k.lens.Query
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.http4k.pageUriFrom
import org.totp.model.FragmentViewModel
import org.totp.model.PageViewModel
import org.totp.search.SearchResultType
import org.totp.search.WeightedSearchResult


class SearchPage(uri: Uri) : PageViewModel(uri)

data class RenderableSearchResult(
    val name: String,
    val type: String,
    val uri: Uri
)

class SearchResultFragment(
    val query: String,
    val results: List<RenderableSearchResult>
) : FragmentViewModel()

val allowedChars: Set<Char> = buildSet {
    addAll('0'..'9')
    addAll('A'..'Z')
    addAll('a'..'z')
    add(' ')
    addAll(listOf('â', 'ê', 'î', 'ô', 'û', 'ŵ', 'ŷ', 'ë'))
    addAll(listOf('Â', 'Ê', 'Î', 'Ô', 'Û', 'Ŵ', 'Ŷ', 'Ë'))
}

fun safeQuery(s: String): String = s
    .trim()
    .take(30)
    .filter { it in allowedChars }


class SearchPageHandler(renderer: TemplateRenderer) : HttpHandler {
    val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

    override fun invoke(request: Request): Response {
        return Response(Status.OK).with(viewLens of SearchPage(pageUriFrom(request)))
    }
}

class SearchResultsHandler(renderer: TemplateRenderer, val searcher: (String) -> List<WeightedSearchResult>) :
    HttpHandler {
    val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()
    val query = Query.defaulted("q", "")

    override fun invoke(request: Request): Response {

        val search = query(request).let(::safeQuery)

        return Response(Status.OK).with(
            viewLens of SearchResultFragment(
                query = search,
                results = searcher(search).take(20).map {
                    when (it.type) {
                        SearchResultType.Constituency -> RenderableSearchResult(it.text, "Constituency", it.uri)
                        SearchResultType.WaterCompany -> RenderableSearchResult(it.text, "Water Company", it.uri)
                        SearchResultType.Place -> RenderableSearchResult(it.text, "Place", it.uri)
                        SearchResultType.Overflow -> RenderableSearchResult(it.text, "Overflow", it.uri)
                        SearchResultType.River -> RenderableSearchResult(it.text, "River", it.uri)
                    }
                }
            )
        )
    }
}
