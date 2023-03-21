package org.totp

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.events.AutoMarshallingEvents
import org.http4k.events.EventFilters
import org.http4k.events.then
import org.http4k.filter.DebuggingFilters
import org.http4k.filter.ResponseFilters
import org.http4k.filter.ServerFilters
import org.http4k.format.Jackson
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.uri
import org.http4k.routing.ResourceLoader
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.Undertow
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel
import org.http4k.template.viewModel
import org.totp.pages.ConstituencyPageHandler
import org.totp.pages.EnsureSuccessfulResponse
import org.totp.pages.Http4kTransaction
import org.totp.pages.IncomingHttpRequest
import org.totp.pages.NoOp
import org.totp.pages.SitemeshFilter
import java.time.Clock


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

fun decoratorSelector(handler: HttpHandler): (Http4kTransaction) -> String {
    return { (req, _) ->
        handler(
            Request(Method.GET, "/decorator/main").query(
                "uri",
                req.uri.toString()
            )
        )
            .bodyString()
    }
}

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
            .then(EventFilters.AddZipkinTraces())
            .then(EventFilters.AddServiceName("service-name-here"))
            .then(AutoMarshallingEvents(Jackson))

    val inboundFilters = (if (debug) DebuggingFilters.PrintRequestAndResponse(debugStream = true) else NoOp())
        .then(ServerFilters.RequestTracing())
        .then(ResponseFilters.ReportHttpTransaction {
            events(IncomingHttpRequest(it.request.uri, it.response.status.code, it.duration.toMillis()))
        })
        .then(ServerFilters.CatchAll())

    val internalRoutes = InternalRoutes()

    val sitemesh = SitemeshFilter(
        decoratorSelector = decoratorSelector(EnsureSuccessfulResponse().then(internalRoutes))
    )

    val server = Undertow().toServer(
        inboundFilters.then(
            routes(
                "/constituency/{constituency}" bind sitemesh.then(ConstituencyPageHandler()),
                "/assets" bind static(ResourceLoader.Directory("src/main/resources/assets"))
            )
        )
    )

    server.start()

    print("Server started at ${Uri.of("http://localhost:" + server.port())}")

    server.block()
}