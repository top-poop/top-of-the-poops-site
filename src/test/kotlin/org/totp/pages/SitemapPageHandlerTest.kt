package org.totp.pages

import org.http4k.core.*
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.strikt.bodyString
import org.http4k.strikt.contentType
import org.junit.jupiter.api.Test
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory


class SitemapPageHandlerTest {

    val uriWithParams = "/page?thing=thing&bob=bob"
    val uriPlain = "/page"

    val service = routes(
        "/sitemap.xml" bind Method.GET to SitemapHandler(
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
                queryString("/urlset/url[1]/loc").isEqualTo("https://totp.example.com/page")
                queryString("/urlset/url[2]/loc").isEqualTo("https://totp.example.com/page?thing=thing&bob=bob")
            }
        }
    }
}


val Assertion.Builder<String>.isXml get() = get { Xml.parse(this) }
fun Assertion.Builder<Document>.queryString(path: String) = get { this.queryString(path) }

object Xml {
    fun parse(xml: String): Document {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            InputSource(
                StringReader(xml)
            )
        )
    }
}

fun Document.queryString(path: String): String {
    return XPathFactory.newInstance().newXPath().evaluate(path, this)
}

fun Document.queryNodeList(path: String): NodeList {
    return XPathFactory.newInstance().newXPath().evaluate(path, this, XPathConstants.NODESET) as NodeList
}

fun NodeList.asSequence(): Sequence<Node> {
    return (0..<this.length)
        .asSequence()
        .map { this.item(it) }
}