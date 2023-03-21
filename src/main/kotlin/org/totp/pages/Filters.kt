package org.totp.pages

import org.http4k.core.*
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.model.error.HttpError
import java.io.IOException
import java.lang.IllegalStateException

fun NoOp() = Filter { next -> { next(it) } }

object HtmlPageErrorFilter {
    operator fun invoke(renderer: TemplateRenderer): Filter {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return Filter { next ->
            {
                try {
                    val response = next(it)
                    when (response.status.successful) {
                        true -> response
                        false -> Response(response.status).with(viewLens of HttpError(response.status))
                    }
                } catch (e: Throwable) {
                    val status = Status.INTERNAL_SERVER_ERROR
                    Response(status).with(viewLens of HttpError(status, e))
                }
            }
        }
    }
}

object EnsureSuccessfulResponse {
    operator fun invoke(): Filter = Filter { next -> {
        val response = next(it)
        if ( response.status.successful ) {
            response
        }
        else throw IOException("Upstream had error ${response.status} for ${it.uri}")
    }}
}
