package org.totp.pages

import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.with
import org.http4k.lens.Header.CONTENT_TYPE
import org.totp.model.data.BathingRank
import org.totp.model.data.RiverRank
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.stream.XMLOutputFactory

object SitemapHandler {
    operator fun invoke(siteBaseUri: Uri, uris: () -> List<Uri>): HttpHandler {
        val factory = XMLOutputFactory.newFactory().also {
            it.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
        }

        return { request ->

            val sw = StringWriter()
            val writer = factory.createXMLStreamWriter(sw)

            writer.writeStartDocument()
            val nsURI = "http://www.sitemaps.org/schemas/sitemap/0.9"
            writer.setDefaultNamespace(nsURI)
            writer.writeStartElement(nsURI,"urlset",)

            uris().map {
                it
                    .scheme(siteBaseUri.scheme)
                    .host(siteBaseUri.host)
                    .port(siteBaseUri.port)
            }.forEach { uri ->
                writer.writeStartElement("url")
                writer.writeStartElement("loc")
                writer.writeCharacters(uri.toString())
                writer.writeEndElement()
                writer.writeEndElement()
            }

            writer.writeEndElement()
            writer.writeEndDocument()

            Response(Status.OK)
                .with(
                    CONTENT_TYPE of ContentType.TEXT_XML
                ).body(
                    sw.toString()
                )
        }
    }
}

object SitemapUris {
    operator fun invoke(
        constituencies: () -> List<ConstituencyRank>,
        riverRankings: () -> List<RiverRank>,
        beachRankings: () -> List<BathingRank>
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
            ).plus(
                beachRankings().map {
                    it.toRenderable().beach.uri
                }
            )
                .toSet()
                .toList()
        }
    }
}