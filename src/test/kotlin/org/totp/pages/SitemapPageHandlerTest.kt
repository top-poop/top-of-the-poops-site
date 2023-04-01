package org.totp.pages

import org.http4k.core.ContentType
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.strikt.bodyString
import org.http4k.strikt.contentType
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.w3c.dom.Document
import org.xml.sax.InputSource
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory


class SitemapPageHandlerTest {

    val uriWithParams = "/page?thing=thing&bob=bob"
    val uriPlain = "/page"

    val service = routes(
        "/sitemap.xml" bind Method.GET to SitemapHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            siteBaseUri = Uri.of("https://totp.example.com"),
            uris = {
                listOf(
                    Uri.of(uriPlain),
                    Uri.of(uriWithParams),
                )
            },
        )
    )

    @Test
    fun `renders the sitemap`() {
        val response = service(Request(Method.GET, "/sitemap.xml"))

        expectThat(response) {
            get { status }.isEqualTo(Status.OK)
            contentType.isEqualTo(ContentType.TEXT_XML)
            bodyString.isXml.and {
                xpath("/urlset/url[1]/loc").isEqualTo("https://totp.example.com/page")
                xpath("/urlset/url[2]/loc").isEqualTo("https://totp.example.com/page?thing=thing&bob=bob")
            }
        }
    }
}


val Assertion.Builder<String>.isXml get() = get { Xml.parse(this) }
fun Assertion.Builder<Document>.xpath(path: String) = get { Xml.xpath(path, this) }

object Xml {
    fun parse(xml: String): Document {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            InputSource(
                StringReader(xml)
            )
        )
    }

    fun xpath(path: String, document: Document): String {
        return XPathFactory.newInstance().newXPath().evaluate(path, document)
    }
}