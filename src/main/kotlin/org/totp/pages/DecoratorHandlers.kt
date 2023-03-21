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
import org.http4k.lens.uri
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel
import org.http4k.template.viewModel

object Decorators {

    data class Page(val decoratorName: String, val uri: Uri) : ViewModel {
        override fun template(): String {
            return decoratorName
        }
    }

    operator fun invoke(): HttpHandler {
        val renderer = HandlebarsTemplates().HotReload(
            "src/main/resources/templates/page/org/totp",
        )
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        val decoratorName = Path.of("decorator")
        val uri = Query.uri().required("uri")

        return {
            Response(Status.OK)
                .with(
                    viewLens of Page(
                        decoratorName = decoratorName(it).let { name -> "decorators/$name" },
                        uri = uri(it)
                    )
                )
        }
    }
}


fun httpHandlerDecoratorSelector(
    handler: HttpHandler,
    mapper: (Http4kTransaction) -> Uri
): (Http4kTransaction) -> String {
    return { tx ->
        handler(
            Request(Method.GET, mapper(tx)).query(
                "uri",
                tx.first.uri.toString()
            )
        )
            .bodyString()
    }
}

