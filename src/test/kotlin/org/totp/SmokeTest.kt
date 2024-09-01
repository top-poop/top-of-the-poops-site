package org.totp

import org.http4k.client.OkHttp
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Uri
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.totp.pages.Xml
import org.totp.pages.asSequence
import org.totp.pages.queryNodeList
import java.util.concurrent.CompletionService
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors


class SmokeTest {

    val uri = Uri.of("http://localhost:8000")
    val client = OkHttp()
    val executor = Executors.newFixedThreadPool(10)

    @AfterEach fun tearDown() {
        executor.shutdown()
    }

    @Test
    @Disabled("a bit slow, takes 11s")
    fun `traversing the sitemap`() {
        val sitemap = Xml.parse(client(Request(Method.GET, uri.path("/sitemap.xml"))).bodyString())

        val result = sitemap.queryNodeList("/urlset/url/loc/text()").asSequence().toList()
        val completionService: CompletionService<Pair<Uri, Response>> = ExecutorCompletionService(executor)

        result.forEach {
            completionService.submit {
                val pageInSite = Uri.of(it.nodeValue).copy(scheme = "http", host = "localhost", port = 8000)
                Pair(pageInSite, client(Request(Method.GET, pageInSite)))
            }
        }

        result.indices.forEach {
            completionService.take().get().takeUnless { it.second.status.successful }?.also { println("${it.first} ${it.second.status}") }
        }
    }
}