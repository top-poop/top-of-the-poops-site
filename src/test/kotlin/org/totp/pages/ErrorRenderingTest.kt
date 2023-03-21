package org.totp.pages

import org.http4k.core.*
import org.http4k.strikt.bodyString
import org.http4k.strikt.contentType
import org.http4k.template.HandlebarsTemplates
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo

class ErrorRenderingTest {

    val renderer = HandlebarsTemplates().HotReload("src/main/resources/templates/page")
    val filter = HtmlPageErrorFilter(renderer)

    @Test
    fun `renders an error page when there is an error`() {
        val app = filter.then { Response(Status.NOT_FOUND) }
        val response = app(Request(Method.GET, "/"))
        expectThat(response).bodyString.contains("Page Not Found")
        expectThat(response).contentType.isEqualTo(ContentType.TEXT_HTML)
    }

    @Test
    fun `renders original content otherwise`() {
        val app = filter.then { Response(Status.OK).body("Yo") }
        val response = app(Request(Method.GET, "/"))
        expectThat(response).bodyString.isEqualTo("Yo")
    }
}