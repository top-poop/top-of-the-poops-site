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
import org.totp.model.PageViewModel
import org.totp.model.data.RiverRank

class SitemapPage(uri: Uri, val uris: List<Uri>) : PageViewModel(uri)

object SitemapHandler {
    operator fun invoke(siteBaseUri: Uri, renderer: TemplateRenderer, uris: () -> List<Uri>): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_XML).toLens()
        return { request ->
            Response(Status.OK)
                .with(
                    viewLens of SitemapPage(
                        siteBaseUri,
                        uris().map {
                            it
                                .scheme(siteBaseUri.scheme)
                                .host(siteBaseUri.host)
                                .port(siteBaseUri.port)
                        }
                    )
                )
        }
    }
}

object SitemapUris {
    operator fun invoke(
        constituencies: () -> List<ConstituencyRank>,
        riverRankings: () -> List<RiverRank>,
    ): () -> List<Uri> {
        return {
            listOf(
                Uri.of("/media"),
                Uri.of("/constituencies"),
                Uri.of("/beaches"),
                Uri.of("/rivers"),
            ).plus(
                constituencies().map {
                    it.constituencyName.toRenderable().uri
                }
            ).plus(
                riverRankings().flatMap {
                    it.toRenderable().let { listOf(it.river.uri, it.company.uri) }
                }
            ).toSet()
                .toList()
        }
    }
}