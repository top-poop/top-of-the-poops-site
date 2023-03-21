package org.totp.pages

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.with
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.string
import org.http4k.lens.uri
import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel
import org.http4k.template.viewModel

object Decorators {

    data class Page(val decoratorName: String, val uri: Uri, val title: String) : ViewModel {
        override fun template(): String {
            return decoratorName
        }
    }

    // possibly a bit odd to render the decorator handlebars in this call, could be  just a data endpoint?
    operator fun invoke(renderer: TemplateRenderer): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        val decoratorName = Path.of("decorator")
        val uri = Query.uri().required("uri")
        val title = Query.string().required("title")

        return {
            Response(Status.OK)
                .with(
                    viewLens of Page(
                        decoratorName = decoratorName(it).let { name -> "decorators/$name" },
                        uri = uri(it),
                        title = title(it)
                    )
                )
        }
    }
}


fun httpHandlerDecoratorSelector(
    handler: HttpHandler,
    mapper: (Http4kTransaction) -> Uri
): (DecoratorContext) -> String {
    return { context ->
        handler(
            Request(Method.GET, mapper(context.tx))
                .query(
                    "uri",
                    context.tx.first.uri.toString()
                )
                .query("title", context.title)
        )
            .bodyString()
    }
}

