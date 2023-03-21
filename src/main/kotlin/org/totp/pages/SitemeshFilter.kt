package org.totp.pages

import org.http4k.core.ContentType
import org.http4k.core.Filter
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.lens.Header.CONTENT_TYPE
import org.sitemesh.SiteMeshContext
import org.sitemesh.content.Content
import org.sitemesh.content.ContentProcessor
import org.sitemesh.content.tagrules.TagBasedContentProcessor
import org.sitemesh.content.tagrules.decorate.DecoratorTagRuleBundle
import org.sitemesh.content.tagrules.html.CoreHtmlTagRuleBundle
import java.nio.CharBuffer

typealias Http4kTransaction = Pair<Request, Response>

data class DecoratorContext(val tx: Http4kTransaction, val title: String)

object SitemeshControls {
    fun contentTypes(types: Set<ContentType>): (Http4kTransaction) -> Boolean =
        { types.contains(CONTENT_TYPE(it.second)) }

    fun onlyHtmlPages() = contentTypes(setOf(ContentType.TEXT_HTML))
}

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
        decoratorSelector: (DecoratorContext) -> String,
        shouldDecorate: (Http4kTransaction) -> Boolean = { true },
        contentProcessor: ContentProcessor = TagBasedContentProcessor(
            CoreHtmlTagRuleBundle(),
            DecoratorTagRuleBundle()
        ),
    ): Filter = Filter { next ->
        {
            val response = next(it)
            val tx = it to response
            if (shouldDecorate(tx)) {
                val content = contentProcessor.build(CharBuffer.wrap(response.bodyString()), null)
                val tx = DecoratorContext(tx, content.extractedProperties.getChild("title").toString())
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