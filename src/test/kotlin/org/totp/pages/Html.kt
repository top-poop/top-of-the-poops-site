package org.totp.pages

import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.strikt.status
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import strikt.api.Assertion
import strikt.api.DescribeableBuilder
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.first
import strikt.assertions.isEqualTo

fun Assertion.Builder<Document>.select(selector: String): DescribeableBuilder<Elements> {
    return get { this.select(selector) }
}

fun Assertion.Builder<Element>.attribute(name: String): DescribeableBuilder<String> {
    return get { this.attr(name) }
}

fun Assertion.Builder<Document>.twitterImageUri(): DescribeableBuilder<String> {
    return this.select("meta[name='twitter:image']").first().attribute("content")
}


object Html {
    operator fun invoke(
        response: Response,
        expected: (Response) -> Unit = { expectThat(it).status.isEqualTo(Status.OK) }
    ): Document {
        expected(response)

        val body = response.bodyString()

        expectThat(body).not().contains("Renderable")

        return Jsoup.parse(body)
    }
}
