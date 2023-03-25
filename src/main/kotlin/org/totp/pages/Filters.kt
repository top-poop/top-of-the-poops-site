package org.totp.pages

import org.http4k.core.*
import org.http4k.events.Event
import org.http4k.events.Events
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.model.error.HttpError
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.IllegalStateException

fun NoOp() = Filter { next -> { next(it) } }

data class UncaughtExceptionEvent(val message: String, val stacktrace: String): Event {
    constructor(t: Throwable) : this(t.message ?: "No message", t.stackTraceToString())
}

object HtmlPageErrorFilter {
    operator fun invoke(events: Events, renderer: TemplateRenderer): Filter {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return Filter { next ->
            {
                try {
                    val response = next(it)
                    if (response.status.clientError or response.status.serverError) {
                        Response(response.status).with(viewLens of HttpError(response.status))
                    }
                    else {
                        response
                    }
                } catch (t: Throwable) {
                    events(UncaughtExceptionEvent(t))
                    val status = Status.INTERNAL_SERVER_ERROR
                    Response(status).with(viewLens of HttpError(status, t))
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
