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
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.Undertow
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.TemplateRenderer
import org.totp.events.ServerStartedEvent
import org.totp.http4k.StandardFilters
import org.totp.model.TotpHandlebars
import org.totp.model.data.BeachRankings
import org.totp.model.data.ConstituencyBoundaries
import org.totp.model.data.ConstituencyCSOs
import org.totp.model.data.ConstituencyRankings
import org.totp.model.data.MediaAppearances
import org.totp.model.data.WaterCompanies
import org.totp.pages.BeachesPageHandler
import org.totp.pages.ConstituenciesPageHandler
import org.totp.pages.ConstituencyPageHandler
import org.totp.pages.EnsureSuccessfulResponse
import org.totp.pages.HomepageHandler
import org.totp.pages.MediaPageHandler
import java.time.Clock


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
    val debugging = EnvironmentKey.boolean().required("DEBUG_MODE", "Print all request and response")

    val defaultConfig = Environment.defaults(
        isDevelopment of false,
        debugging of false,
        dataServiceUri of Uri.of("http://data")
    )

    val environment = Environment.JVM_PROPERTIES overrides
            Environment.ENV overrides
            defaultConfig

    val isDevelopmentEnvironment = isDevelopment(environment)

    val events =
        EventFilters.AddTimestamp(Clock.systemUTC())
            .then(EventFilters.AddEventName())
            .then(EventFilters.AddServiceName("pages"))
            .then(AutoMarshallingEvents(Jackson))


    val renderer = Resources.templates(
        templates = TotpHandlebars.templates(),
        devMode = isDevelopmentEnvironment
    )

    val inboundFilters = StandardFilters.incoming(events, debugging(environment))
    val outboundFilters = StandardFilters.outgoing(events)

    val dataClient = if (isDevelopmentEnvironment) {
        outboundFilters.then(static(ResourceLoader.Directory("services/data/datafiles")))
    } else {
        EnsureSuccessfulResponse()
            .then(SetBaseUriFrom(Uri.of("/data")))
            .then(ClientFilters.SetHostFrom(dataServiceUri(environment)))
            .then(outboundFilters)
            .then(OkHttp())
    }

    val data2021 = SetBaseUriFrom(Uri.of("/v1/2021")).then(dataClient)

    val server = Undertow(
        if (isDevelopmentEnvironment) 8000 else {
            80
        }
    ).toServer(
        routes(
            "/" bind inboundFilters.then(
                routes(
                    "/" bind HomepageHandler(
                        renderer = renderer,
                        consituencyRankings = ConstituencyRankings(data2021),
                        beachRankings = BeachRankings(data2021),
                        appearances = MediaAppearances(dataClient),
                        companies = WaterCompanies(dataClient)
                    ),
                    "/media" bind MediaPageHandler(
                        renderer = renderer,
                        appearances = MediaAppearances(dataClient)
                    ),
                    "/constituencies" bind ConstituenciesPageHandler(
                        renderer = renderer,
                        consituencyRankings = ConstituencyRankings(data2021)
                    ),
                    "/beaches" bind BeachesPageHandler(
                        renderer = renderer,
                        beachRankings = BeachRankings(data2021)
                    ),
                    "/constituency/{constituency}" bind ConstituencyPageHandler(
                        renderer = renderer,
                        constituencySpills = ConstituencyCSOs(data2021),
                        constituencyBoundary = ConstituencyBoundaries(
                            SetBaseUriFrom(Uri.of("/constituencies")).then(dataClient)
                        )
                    )
                )
            ),
            "/data" bind inboundFilters.then(static(ResourceLoader.Directory("services/data/datafiles"))),
            "/assets" bind static(Resources.assets(isDevelopmentEnvironment)),
        )
    )

    server.start()
    events(ServerStartedEvent(Uri.of("http://localhost:" + server.port()), isDevelopmentEnvironment))
    server.block()
}