package org.totp.pages

import org.http4k.core.*
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.viewModel
import org.totp.model.error.HttpError

fun NoOp() = Filter { next -> { next(it) } }

object HtmlPageErrorFilter {
    val renderer = HandlebarsTemplates().HotReload("src/main/resources/templates/page")
    val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

    operator fun invoke(): Filter = Filter { next ->
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