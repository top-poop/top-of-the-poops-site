package org.totp

import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.events.AutoMarshallingEvents
import org.http4k.events.EventFilters
import org.http4k.events.HttpEvent
import org.http4k.events.then
import org.http4k.filter.DebuggingFilters
import org.http4k.filter.ResponseFilters
import org.http4k.filter.ServerFilters
import org.http4k.format.Jackson
import org.http4k.routing.ResourceLoader
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.Undertow
import org.totp.model.data.csoSummaries
import org.totp.pages.ConstituencyPageHandler
import org.totp.pages.Decorators
import org.totp.pages.EnsureSuccessfulResponse
import org.totp.pages.NoOp
import org.totp.pages.ServerStartedEvent
import org.totp.pages.SitemeshFilter
import org.totp.pages.httpHandlerDecoratorSelector
import java.nio.file.Path
import java.time.Clock


object InternalRoutes {
    operator fun invoke(): RoutingHttpHandler {
        return routes(
            "/decorator/{decorator}" bind Decorators()
        )
    }
}


fun main() {
    val clock = Clock.systemUTC()
    val debug = false

    val events =
        EventFilters.AddTimestamp(clock)
            .then(EventFilters.AddEventName())
            .then(AutoMarshallingEvents(Jackson))

    val inboundFilters = (if (debug) DebuggingFilters.PrintRequestAndResponse(debugStream = true) else NoOp())
        .then(ServerFilters.RequestTracing())
        .then(ResponseFilters.ReportHttpTransaction {
            events(HttpEvent.Incoming(it))
        })
        .then(ServerFilters.CatchAll())

    val internalRoutes = InternalRoutes()

    val sitemesh = SitemeshFilter(
        decoratorSelector = httpHandlerDecoratorSelector(
            handler = EnsureSuccessfulResponse().then(internalRoutes),
            mapper = { Uri.of("/decorator/main") }
        )
    )

    val server = Undertow().toServer(
        routes(
            "" bind inboundFilters.then(sitemesh).then(
                routes(
                    "/constituency/{constituency}" bind ConstituencyPageHandler(csoSummaries(Path.of("/home/richja/dev/gis/web/data/generated/spills-all.json")))
                )
            ),
            "/assets" bind static(ResourceLoader.Directory("src/main/resources/assets"))
        )
    )

    server.start()
    events(ServerStartedEvent(Uri.of("http://localhost:" + server.port())))
    server.block()
}