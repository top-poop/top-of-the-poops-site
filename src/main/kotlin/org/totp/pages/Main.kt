package org.totp.pages

import org.http4k.core.*
import org.http4k.core.ContentType.Companion.TEXT_HTML
import org.http4k.events.AutoMarshallingEvents
import org.http4k.events.EventFilters
import org.http4k.events.then
import org.http4k.filter.DebuggingFilters
import org.http4k.filter.ResponseFilters
import org.http4k.filter.ServerFilters
import org.http4k.format.Jackson
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Undertow
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel
import org.http4k.template.viewModel
import java.time.Clock

data class Constituency(val name: String) : ViewModel

object ConstituencyRendering {
    operator fun invoke(): HttpHandler {
        val renderer = HandlebarsTemplates().HotReload("src/main/resources/templates/page")
        val viewLens = Body.viewModel(renderer, TEXT_HTML).toLens()

        return { _: Request ->
            Response(Status.OK).with(viewLens of Constituency("bob"))
        }
    }
}

object Main {
    operator fun invoke(): RoutingHttpHandler {
        return routes(
            "/" bind { _: Request -> Response(Status.OK) },
            "/constituency" bind ConstituencyRendering()
        )
    }
}


fun main() {
    val clock = Clock.systemUTC()
    val debug = false

    val events =
        EventFilters.AddTimestamp(clock)
            .then(EventFilters.AddEventName())
            .then(EventFilters.AddZipkinTraces())
            .then(EventFilters.AddServiceName("service-name-here"))
            .then(AutoMarshallingEvents(Jackson))

    val inboundFilters = (if (debug) DebuggingFilters.PrintRequestAndResponse(debugStream = true) else NoOp())
        .then(ServerFilters.RequestTracing())
        .then(ResponseFilters.ReportHttpTransaction {
            events(IncomingHttpRequest(it.request.uri, it.response.status.code, it.duration.toMillis()))
        })
        .then(ServerFilters.CatchAll())


    val server = Undertow().toServer(inboundFilters.then(Main()))

    server.start()

    print("Server started at ${Uri.of("http://localhost:" + server.port())}")

    server.block()
}