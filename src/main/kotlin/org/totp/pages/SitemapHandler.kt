package org.totp.pages

import org.http4k.core.*
import org.http4k.lens.Header.CONTENT_TYPE
import org.totp.db.StreamId
import org.totp.model.data.BathingRank
import org.totp.model.data.RiverRank
import org.totp.model.data.toRenderable
import java.io.StringWriter
import java.time.Instant
import javax.xml.stream.XMLOutputFactory

data class SitemapEntry(val uri: Uri, val updated: Instant? = null)

private fun Uri.sitemap() = SitemapEntry(this)
private fun Uri.sitemap(updated: Instant) = SitemapEntry(this, updated)

class SitemapXml(val base: Uri) {

    private val factory = XMLOutputFactory.newFactory().also {
        it.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true)
    }

    fun createSitemapXml(
        uris: List<SitemapEntry>,
    ): String {
        val sw = StringWriter()
        val writer = factory.createXMLStreamWriter(sw)

        writer.writeStartDocument()
        val nsURI = "http://www.sitemaps.org/schemas/sitemap/0.9"
        writer.setDefaultNamespace(nsURI)
        writer.writeStartElement(nsURI, "urlset")

        uris.map {
            base.extend(it.uri)
        }.forEach { uri ->
            writer.writeStartElement("url")
            writer.writeStartElement("loc")
            writer.writeCharacters(uri.toString())
            writer.writeEndElement()
            writer.writeEndElement()
        }

        writer.writeEndElement()
        writer.writeEndDocument()

        return sw.toString()
    }
}

class SitemapIndexXml(val base: Uri) {

    val factory = XMLOutputFactory.newFactory().also {
        it.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true)
    }

    fun createIndex(
        sitemapUris: List<SitemapEntry>
    ): String {

        val sw = StringWriter()
        val writer = factory.createXMLStreamWriter(sw)

        writer.writeStartDocument()
        val nsURI = "http://www.sitemaps.org/schemas/sitemap/0.9"
        writer.setDefaultNamespace(nsURI)
        writer.writeStartElement(nsURI, "sitemapindex")

        sitemapUris.map {
            base.extend(it.uri)
        }.forEach { uri ->
            writer.writeStartElement("sitemap")
            writer.writeStartElement("loc")
            writer.writeCharacters(uri.toString())
            writer.writeEndElement()
            writer.writeEndElement()
        }

        writer.writeEndElement()
        writer.writeEndDocument()

        return sw.toString()
    }
}

abstract class AbstractSitemapHandler(siteBaseUri: Uri) : HttpHandler {

    val sitemapXml = SitemapXml(siteBaseUri)

    abstract fun entries(): List<SitemapEntry>
    override fun invoke(p1: Request): Response {
        return Response(Status.OK)
            .with(
                CONTENT_TYPE of ContentType.TEXT_XML
            ).body(
                sitemapXml.createSitemapXml(entries())
            )
    }
}

class SitemapConstituencyUris(
    siteBaseUri: Uri,
    val constituencies: () -> List<ConstituencyRank>
) :
    AbstractSitemapHandler(siteBaseUri) {
    override fun entries(): List<SitemapEntry> = constituencies().flatMap {
        listOf(
            it.constituencyName.toRenderable().uri,
            it.constituencyName.toRenderable(linkLive = true).uri,
            it.constituencyName.toRenderable(linkLive = true).uri.query("year", "2025")
        ).map(Uri::sitemap)
    }
}

class SitemapPlaceUris(
    siteBaseUri: Uri,
    val places: () -> List<PlaceRank>
) : AbstractSitemapHandler(siteBaseUri) {
    override fun entries(): List<SitemapEntry> = places().flatMap {
        listOf(it.placeName.toRenderable().uri)
    }.map(Uri::sitemap)
}

class SitemapBeachUris(
    siteBaseUri: Uri,
    val beaches: () -> List<BathingRank>
) : AbstractSitemapHandler(siteBaseUri) {
    override fun entries(): List<SitemapEntry> = beaches().flatMap {
        listOf(it.beach.toRenderable().uri)
    }.map(Uri::sitemap)
}

class SitemapRiverUris(
    siteBaseUri: Uri,
    val rivers: () -> List<RiverRank>
) : AbstractSitemapHandler(siteBaseUri) {
    override fun entries(): List<SitemapEntry> =
        rivers()
            .map { it.toRenderable() }
            .filterNot { it.river.uri.toString().endsWith("/") }
            .flatMap {
                listOf(it.river.uri, it.company.uri)
            }
            .map(Uri::sitemap)
}

class SitemapOverflowUris(
    siteBaseUri: Uri,
    val rivers: () -> List<StreamId>
) : AbstractSitemapHandler(siteBaseUri) {
    override fun entries(): List<SitemapEntry> =
        rivers()
            .flatMap {
                listOf(
                    it.toRenderable().uri,
                    it.toRenderable(2025).uri
                )
            }
            .map(Uri::sitemap)
}

class SitemapStaticUris(
    siteBaseUri: Uri,
) : AbstractSitemapHandler(siteBaseUri) {
    override fun entries(): List<SitemapEntry> = listOf(
        Uri.of("/media"),
        Uri.of("/now"),
        Uri.of("/support"),
        Uri.of("/constituencies"),
        Uri.of("/beaches"),
        Uri.of("/rivers"),
        Uri.of("/places"),
    ).map { it.sitemap() }
}

class SitemapIndexHandler(val base: Uri, val locations: List<Uri>) : HttpHandler {
    private val index = SitemapIndexXml(base)
    override fun invoke(p1: Request): Response {
        return Response(Status.OK)
            .with(
                CONTENT_TYPE of ContentType.TEXT_XML
            ).body(
                index.createIndex(locations.map(Uri::sitemap))
            )
    }
}
