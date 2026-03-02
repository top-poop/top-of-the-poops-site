package org.totp.pages

import org.http4k.core.*
import org.http4k.lens.Header.CONTENT_TYPE
import org.totp.db.StreamId
import org.totp.model.data.BathingRank
import org.totp.model.data.CompanyName
import org.totp.model.data.RiverRank
import org.totp.model.data.toRenderable
import java.io.StringWriter
import java.time.Clock
import java.time.Instant
import javax.xml.stream.XMLOutputFactory

data class SitemapEntry(val uri: Uri, val updated: Instant? = null)

private fun Uri.sitemap() = SitemapEntry(this)
private fun Uri.sitemap(updated: Instant?) = SitemapEntry(this, updated)

class SitemapXml(val base: Uri) {

    private val factory = XMLOutputFactory.newFactory().also {
        it.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true)
    }

    fun createSitemapXml(
        entries: List<SitemapEntry>,
    ): String {
        val sw = StringWriter()
        val writer = factory.createXMLStreamWriter(sw)

        writer.writeStartDocument()
        val nsURI = "http://www.sitemaps.org/schemas/sitemap/0.9"
        writer.setDefaultNamespace(nsURI)
        writer.writeStartElement(nsURI, "urlset")

        entries.forEach {
            writer.writeStartElement("url")

            it.uri.also {
                writer.writeStartElement("loc")
                writer.writeCharacters(base.extend(it).toString())
                writer.writeEndElement()
            }

            it.updated?.also {
                writer.writeStartElement("lastmod")
                writer.writeCharacters(it.toString())
                writer.writeEndElement()
            }


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
        entries: List<SitemapEntry>
    ): String {

        val sw = StringWriter()
        val writer = factory.createXMLStreamWriter(sw)

        writer.writeStartDocument()
        val nsURI = "http://www.sitemaps.org/schemas/sitemap/0.9"
        writer.setDefaultNamespace(nsURI)
        writer.writeStartElement(nsURI, "sitemapindex")

        entries.forEach {
            writer.writeStartElement("sitemap")

            it.uri.also {
                writer.writeStartElement("loc")
                writer.writeCharacters(base.extend(it).toString())
                writer.writeEndElement()
            }

            it.updated?.also {
                writer.writeStartElement("lastmod")
                writer.writeCharacters(it.toString())
                writer.writeEndElement()
            }

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
    val clock: Clock,
    siteBaseUri: Uri,
    val constituencies: () -> List<ConstituencyRank>
) :
    AbstractSitemapHandler(siteBaseUri) {
    override fun entries(): List<SitemapEntry> {

        val lastUpdated = previousQuarterUtc(clock.instant())

        return constituencies()
            .sortedBy { it.constituencyName }
            .flatMap {
                listOf(
                    it.constituencyName.toRenderable().uri.sitemap(),
                    it.constituencyName.toRenderable(linkLive = true).uri.sitemap(lastUpdated),
                    it.constituencyName.toRenderable(linkLive = true).uri.query("year", "2025").sitemap()
                )
            }
    }
}

class SitemapCompanyUris(
    val clock: Clock,
    siteBaseUri: Uri,
    val constituencies: () -> List<CompanyName>
) :
    AbstractSitemapHandler(siteBaseUri) {
    override fun entries(): List<SitemapEntry> {

        val lastUpdated = previousQuarterUtc(clock.instant())

        return constituencies()
            .sortedBy { it.value }
            .flatMap {
                listOf(
                    it.toRenderable().uri.sitemap(lastUpdated),
                )
            }
    }
}

class SitemapPlaceUris(
    siteBaseUri: Uri,
    val places: () -> List<PlaceRank>
) : AbstractSitemapHandler(siteBaseUri) {
    override fun entries(): List<SitemapEntry> = places()
        .flatMap {
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

fun previousQuarterUtc(input: Instant): Instant {
    val quarterSeconds = 15 * 60L
    val floored = (input.epochSecond / quarterSeconds) * quarterSeconds
    return Instant.ofEpochSecond(floored)
}

class SitemapOverflowUris(
    val clock: Clock,
    siteBaseUri: Uri,
    val ids: () -> List<StreamId>,
    val year: Int? = null
) : AbstractSitemapHandler(siteBaseUri) {
    override fun entries(): List<SitemapEntry> {

        val lastUpdated = if (year == null) {
            previousQuarterUtc(clock.instant())
        } else {
            null
        }

        return ids()
            .sortedBy { it.value }
            .flatMap {
                listOf(
                    it.toRenderable(year).uri,
                )
            }
            .map { it.sitemap(updated = lastUpdated) }
    }
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
