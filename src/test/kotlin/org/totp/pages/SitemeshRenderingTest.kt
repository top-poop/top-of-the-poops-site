package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.strikt.bodyString
import org.http4k.strikt.status
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo

class SitemeshRenderingTest {

    val decoratorText =
        "<html><head><title>Hello</title></head><body><sitemesh:write property='body'/></body></html>"

    val filter = SitemeshFilter(decoratorSelector = { decoratorText })

    @Test
    fun `sitemesh decorates page`() {
        val app = filter.then { Response(Status.OK).body("<html><body>Content</body></html>") }
        expectThat(app(Request(Method.GET, "/"))) {
            status.isEqualTo(Status.OK)
            bodyString.contains("Content")
            bodyString.contains("Hello")
        }
    }
}