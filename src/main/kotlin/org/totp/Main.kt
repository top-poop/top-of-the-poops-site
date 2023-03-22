package org.totp

import org.http4k.client.OkHttp
import org.http4k.cloudnative.env.Environment
import org.http4k.cloudnative.env.EnvironmentKey
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.events.AutoMarshallingEvents
import org.http4k.events.EventFilters
import org.http4k.events.then
import org.http4k.filter.ClientFilters
import org.http4k.filter.ClientFilters.SetBaseUriFrom
import org.http4k.format.Jackson
import org.http4k.lens.boolean
import org.http4k.lens.uri
import org.http4k.routing.ResourceLoader
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.Undertow
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.TemplateRenderer
import org.totp.events.ServerStartedEvent
import org.totp.http4k.StandardFilters
import org.totp.model.TotpHandlebars
import org.totp.model.data.ConstituencyBoundaries
import org.totp.model.data.ConstituencyCSOs
import org.totp.pages.ConstituencyPageHandler
import org.totp.pages.Decorators
import org.totp.pages.EnsureSuccessfulResponse
import org.totp.pages.SitemeshFilter
import org.totp.pages.httpHandlerDecoratorSelector
import java.time.Clock


object InternalRoutes {
    operator fun invoke(renderer: TemplateRenderer): RoutingHttpHandler {
        return routes(
            "/decorator/{decorator}" bind Decorators(renderer)
        )
    }
}

object Resources {

    private val resourceBase = java.nio.file.Path.of("src/main/resources")

    //not very keen on dev mode, but good enough for now.
    fun templates(templates: HandlebarsTemplates, devMode: Boolean): TemplateRenderer {
        return if (devMode) {
            templates.HotReload(resourceBase.resolve("templates/page/org/totp").toString())
        } else {
            templates.CachingClasspath("templates.page.org.totp")
        }
    }

    fun assets(devMode: Boolean): ResourceLoader {
        return if (devMode) {
            ResourceLoader.Directory(resourceBase.resolve("assets").toString())
        } else {
            ResourceLoader.Classpath("/assets")
        }
    }
}


fun main() {

    val isDevelopment =
        EnvironmentKey.boolean().required("DEVELOPMENT_MODE", "Use fake data server (local files) & hot reload")
    val dataServiceUri = EnvironmentKey.uri().required("DATA_SERVICE_URI", "URI for Data Service")

    val defaultConfig = Environment.defaults(
        isDevelopment of true,
        dataServiceUri of Uri.of("http://data")
    )

    val environment = Environment.JVM_PROPERTIES overrides
            Environment.ENV overrides
            defaultConfig

    val isDevelopmentEnvironment = isDevelopment(environment)

    val clock = Clock.systemUTC()

    val events =
        EventFilters.AddTimestamp(clock)
            .then(EventFilters.AddEventName())
            .then(EventFilters.AddServiceName("pages"))
            .then(AutoMarshallingEvents(Jackson))


    val renderer = Resources.templates(
        templates = TotpHandlebars.templates(),
        devMode = isDevelopmentEnvironment
    )

    val internalRoutes = InternalRoutes(renderer)

    val sitemesh = SitemeshFilter(
        decoratorSelector = httpHandlerDecoratorSelector(
            handler = EnsureSuccessfulResponse().then(internalRoutes),
            mapper = { Uri.of("/decorator/main") }
        )
    )

    val inboundFilters = StandardFilters.incoming(events)
    val outboundFilters = StandardFilters.outgoing(events)

    val dataClient = if (isDevelopmentEnvironment) {
        EnsureSuccessfulResponse()
            .then(SetBaseUriFrom(Uri.of("/data")))
            .then(ClientFilters.SetHostFrom(dataServiceUri(environment)))
            .then(outboundFilters)
            .then(OkHttp())
    } else {
        static(ResourceLoader.Directory("services/data/datafiles"))
    }

    val server = Undertow().toServer(
        routes(
            "/" bind inboundFilters.then(sitemesh).then(
                routes(
                    "/constituency/{constituency}" bind ConstituencyPageHandler(
                        renderer = renderer,
                        constituencySpills = ConstituencyCSOs(
                            SetBaseUriFrom(Uri.of("/v1/2021")).then(dataClient)
                        ),
                        constituencyBoundary = ConstituencyBoundaries(
                            SetBaseUriFrom(Uri.of("/constituencies")).then(dataClient)
                        )
                    )
                )
            ),
            "/assets" bind static(Resources.assets(isDevelopmentEnvironment))
        )
    )

    server.start()
    events(ServerStartedEvent(Uri.of("http://localhost:" + server.port()), isDevelopmentEnvironment))
    server.block()
}