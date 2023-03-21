package org.totp.pages

import com.github.jknack.handlebars.ValueResolver
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

object SitemeshControls {
    fun contentTypes(types: Set<ContentType>): (Http4kTransaction) -> Boolean =
        { types.contains(CONTENT_TYPE(it.second)) }

    fun onlyHtmlPages() = contentTypes(setOf(ContentType.TEXT_HTML))
}

class MissingValueResolver : ValueResolver {
    override fun resolve(context: Any?, name: String?): Any {
        throw IllegalStateException("Undefined ${name}")
    }

    override fun resolve(context: Any?): Any {
        TODO()
    }

    override fun propertySet(context: Any?): MutableSet<MutableMap.MutableEntry<String, Any>> {
        TODO()
    }
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
        decoratorSelector: (Http4kTransaction) -> String,
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