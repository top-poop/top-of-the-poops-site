package org.totp.pages

import org.http4k.core.ContentType
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.lens.Header.CONTENT_TYPE
import org.http4k.strikt.bodyString
import org.http4k.strikt.status
import org.http4k.template.ViewModel
import org.junit.jupiter.api.Test
import org.totp.pages.SitemeshControls.onlyHtmlPages
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo

class SitemeshRenderingTest {

    val decoratorText =
        "<html><head><title>Hello</title></head><body><sitemesh:write property='body'/></body></html>"

    val basicFilter = SitemeshFilter(decoratorSelector = { decoratorText })
    val htmlFilter = SitemeshFilter(decoratorSelector = { decoratorText }, shouldDecorate = onlyHtmlPages())

    @Test
    fun `sitemesh decorates page`() {

        val app = basicFilter.then { Response(Status.OK).body("<html><body>Content</body></html>") }
        expectThat(app(Request(Method.GET, "/"))) {
            status.isEqualTo(Status.OK)
            bodyString.contains("Content")
            bodyString.contains("Hello")
        }
    }

    @Test
    fun `empty page is still decorated`() {
        val app = basicFilter.then { Response(Status.OK).body("") }
        expectThat(app(Request(Method.GET, "/"))) {
            status.isEqualTo(Status.OK)
            bodyString.isEqualTo("<html><head><title>Hello</title></head><body></body></html>")
        }
    }

    @Test
    fun `decorates response when criteria are met`() {
        val app = htmlFilter.then { Response(Status.OK).body("Boo").with(CONTENT_TYPE of ContentType.TEXT_HTML) }
        expectThat(app(Request(Method.GET, "/"))) {
            status.isEqualTo(Status.OK)
            bodyString.contains("Boo")
            bodyString.contains("Hello")
        }
    }

    @Test
    fun `does not decorate response when criteria not met`() {
        val app = htmlFilter.then { Response(Status.OK).body("") }
        expectThat(app(Request(Method.GET, "/"))).bodyString.isEqualTo("")
    }
}