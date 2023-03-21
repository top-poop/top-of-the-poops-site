package org.totp.pages

import org.http4k.core.Filter
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.strikt.bodyString
import org.http4k.strikt.status
import org.junit.jupiter.api.Test
import org.sitemesh.SiteMeshContext
import org.sitemesh.content.Content
import org.sitemesh.content.ContentProcessor
import org.sitemesh.content.tagrules.TagBasedContentProcessor
import org.sitemesh.content.tagrules.decorate.DecoratorTagRuleBundle
import org.sitemesh.content.tagrules.html.CoreHtmlTagRuleBundle
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import java.nio.CharBuffer

typealias Http4kTransaction = Pair<Request, Response>

object SitemeshFilter {

    private fun contentContext(content: Content) = object : SiteMeshContext {
        override fun getPath(): String {
            TODO("not required")
        }

        override fun decorate(decoratorName: String?, content: Content?): Content {
            TODO("not required")
        }

        override fun getContentToMerge(): Content {
            return content
        }

        override fun getContentProcessor(): ContentProcessor {
            TODO("not required")
        }
    }

    operator fun invoke(
        decoratorSelector: (Http4kTransaction) -> String,
        shouldDecorate: (Http4kTransaction) -> Boolean = { true },
        contentProcessor: ContentProcessor = TagBasedContentProcessor(CoreHtmlTagRuleBundle(), DecoratorTagRuleBundle()),
    ): Filter = Filter { next ->
        {
            val response = next(it)
            val tx = it to response
            if (shouldDecorate(tx)) {
                val content = contentProcessor.build(CharBuffer.wrap(response.bodyString()), null)
                val decorator = contentProcessor.build(CharBuffer.wrap(decoratorSelector(tx)), contentContext(content))
                response.body(StringBuilder().also { sb ->
                    decorator.data.writeValueTo(sb)
                }.toString())
            } else {
                response
            }
        }
    }
}

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