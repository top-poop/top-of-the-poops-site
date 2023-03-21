package org.totp

import org.http4k.core.*
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
import org.totp.pages.ConstituencyRendering
import org.totp.pages.IncomingHttpRequest
import org.totp.pages.NoOp
import java.time.Clock

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