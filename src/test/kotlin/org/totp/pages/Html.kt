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
import strikt.assertions.get
import strikt.assertions.isEqualTo

fun Assertion.Builder<Document>.select(selector:String): DescribeableBuilder<Elements> {
    return get { this.select(selector) }
}

fun Assertion.Builder<Element>.attribute(name:String): DescribeableBuilder<String> {
    return get { this.attr(name) }
}

fun Assertion.Builder<Document>.twitterImageUri(): DescribeableBuilder<String> {
    return this.select("meta[name='twitter:image']")[0]
        .attribute("content")
}



object Html {
    operator fun invoke(response: Response, expected: (Response) -> Unit = { expectThat(it).status.isEqualTo(Status.OK) }) : Document {
        expected(response)
        return Jsoup.parse(response.bodyString())
    }
}
