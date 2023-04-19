package org.totp.mangling

import org.http4k.client.OkHttp
import org.http4k.core.Method
import org.http4k.core.Uri
import org.http4k.format.Jackson
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.totp.pages.Html

class RetrieivingOGInformation {

    val client = OkHttp()

    @Test
    @Disabled("not a test")
    fun `getting og information`() {

        val uri = "https://www.theboltonnews.co.uk/news/23440662.united-utilities-bolton-areas-top-2-sewage-waterways/"
        val html = Html(client(org.http4k.core.Request(Method.GET, Uri.of(uri))))

        val title = html.selectFirst("title")?.text()
        val ogImage = html.selectFirst("meta[property='og:image']")
        val image = ogImage?.attr("content")

        val json = Jackson.mapper.writeValueAsString(
            mapOf(
                "href" to uri,
                "where" to "xxx",
                "title" to title,
                "image" to image,
                "date" to "",
            )
        )


        println(json)
    }

}