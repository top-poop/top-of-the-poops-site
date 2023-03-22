package org.totp.pages

import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.strikt.status
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import strikt.api.Assertion
import strikt.api.DescribeableBuilder
import strikt.api.expectThat
import strikt.assertions.isEqualTo

fun Assertion.Builder<Document>.select(selector:String): DescribeableBuilder<Elements> {
    return get { this.select(selector) }
}

object Html {
    operator fun invoke(response: Response, expected: (Response) -> Unit = { expectThat(it).status.isEqualTo(Status.OK) }) : Document {
        expected(response)
        return Jsoup.parse(response.bodyString())
    }
}
