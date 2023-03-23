package org.totp.http4k

import org.http4k.core.Filter
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.core.query
import org.http4k.core.then
import org.http4k.core.toParameters
import org.http4k.events.Events
import org.http4k.events.HttpEvent
import org.http4k.filter.ClientFilters
import org.http4k.filter.DebuggingFilters
import org.http4k.filter.ResponseFilters
import org.http4k.filter.ServerFilters
import org.totp.pages.NoOp

object StandardFilters {
    fun incoming(events: Events, debug: Boolean = false): Filter {
        return (if (debug) DebuggingFilters.PrintRequest(debugStream = true) else NoOp())
            .then(ServerFilters.RequestTracing())
            .then(ResponseFilters.ReportHttpTransaction {
                events(HttpEvent.Incoming(it))
            })
            .then(ServerFilters.CatchAll())
    }

    fun outgoing(events: Events): Filter {
        return ClientFilters.RequestTracing()
            .then(ResponseFilters.ReportHttpTransaction {
                events(HttpEvent.Outgoing(it))
            })
    }
}

fun pageUriFrom(request: Request) : Uri {
    val proxied = request.header("X-Forwarded-Host")

    return if (proxied != null) {
        Uri.of((request.header("X-Forwarded-Proto") ?: "http") + "://" + proxied)
    } else {
        Uri.of("http://" + request.header("Host"))
    }.path(request.uri.path).let {
        request.uri.query.toParameters().fold(it) { it, p -> it.query(p.first, p.second) }
    }
}
