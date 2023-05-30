package org.totp.mangling

import org.http4k.client.OkHttp
import org.http4k.core.Method
import org.http4k.core.Uri
import org.http4k.format.Jackson
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.totp.pages.Html
import java.time.Instant
import java.time.ZonedDateTime

class RetrieivingOGInformation {

    val client = OkHttp()

    @Test
    fun `getting og information`() {

        val uri = "https://www.bbc.co.uk/news/articles/cy9008ezyn5o"
        val html = Html(client(org.http4k.core.Request(Method.GET, Uri.of(uri))))

        val title = html.selectFirst("title")?.text()
        val ogImage = html.selectFirst("meta[property='og:image']")
        val image = ogImage?.attr("content")
        val publishDate = html
            .selectFirst("meta[property='article:published_time']")
            ?.attr("content")
            ?.let { ZonedDateTime.parse(it) }
            ?.toLocalDate()
            ?.toString()
            ?: ""

        val json = Jackson.mapper.writeValueAsString(
            mapOf(
                "href" to uri,
                "where" to "xxx",
                "title" to title,
                "image" to image,
                "date" to publishDate,
            )
        )


        println(json)
    }

}