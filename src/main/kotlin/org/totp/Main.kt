package org.totp

import com.google.common.base.Suppliers
import org.http4k.client.OkHttp
import org.http4k.cloudnative.env.Environment
import org.http4k.cloudnative.env.EnvironmentKey
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.events.AutoMarshallingEvents
import org.http4k.events.EventFilters
import org.http4k.events.then
import org.http4k.filter.ClientFilters
import org.http4k.filter.ClientFilters.SetBaseUriFrom
import org.http4k.format.Jackson
import org.http4k.lens.Header.LOCATION
import org.http4k.lens.Query
import org.http4k.lens.boolean
import org.http4k.lens.int
import org.http4k.lens.uri
import org.http4k.lens.value
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.Undertow
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.TemplateRenderer
import org.totp.events.ServerStartedEvent
import org.totp.extensions.Defect
import org.totp.http4k.StandardFilters
import org.totp.model.TotpHandlebars
import org.totp.model.data.AllSpills
import org.totp.model.data.BathingRankings
import org.totp.model.data.BeachBoundaries
import org.totp.model.data.BathingCSOs
import org.totp.model.data.CompanyAnnualSummaries
import org.totp.model.data.ConstituencyBoundaries
import org.totp.model.data.ConstituencyContact
import org.totp.model.data.ConstituencyContacts
import org.totp.model.data.ConstituencyLiveAvailability
import org.totp.model.data.ConstituencyLiveDataLoader
import org.totp.model.data.ConstituencyName
import org.totp.model.data.ConstituencyNeighbours
import org.totp.model.data.ConstituencyRankings
import org.totp.model.data.ConstituencySlug
import org.totp.model.data.MediaAppearances
import org.totp.model.data.RiverRankings
import org.totp.model.data.WaterCompanies
import org.totp.model.data.constituencyCSOs
import org.totp.model.data.constituencyRivers
import org.totp.model.data.toSlug
import org.totp.model.data.waterwayCSOs
import org.totp.pages.BadgesCompaniesHandler
import org.totp.pages.BadgesConstituenciesHandler
import org.totp.pages.BadgesHomeHandler
import org.totp.pages.BadgesRiversHandler
import org.totp.pages.BathingPageHandler
import org.totp.pages.BeachesPageHandler
import org.totp.pages.CompanyPageHandler
import org.totp.pages.ConstituenciesPageHandler
import org.totp.pages.ConstituencyPageHandler
import org.totp.pages.EnsureSuccessfulResponse
import org.totp.pages.HomepageHandler
import org.totp.pages.HtmlPageErrorFilter
import org.totp.pages.MP
import org.totp.pages.MediaPageHandler
import org.totp.pages.RiversPageHandler
import org.totp.pages.SitemapHandler
import org.totp.pages.SitemapUris
import org.totp.pages.WaterwayPageHandler
import org.totp.pages.constituencyNames
import java.time.Clock
import java.util.concurrent.TimeUnit


object Resources {

    private val resourceBase = java.nio.file.Path.of("src/main/resources")

    //not very keen on dev mode, but good enough for now.
    fun templates(templates: HandlebarsTemplates, hotReload: Boolean): TemplateRenderer {
        return if (hotReload) {
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

object OldMapRedirectHandler {
    operator fun invoke(): HttpHandler {

        val constituency = Query.value(ConstituencyName).optional("c")

        return { request ->
            val selected = constituency(request)

            if (selected != null && constituencyNames.contains(selected)) {
                val slug = ConstituencySlug.from(selected)
                Response(Status.TEMPORARY_REDIRECT).with(LOCATION of Uri.of("/constituency/$slug"))
            } else {
                Response(Status.TEMPORARY_REDIRECT).with(LOCATION of Uri.of("/constituencies"))
            }
        }
    }
}

fun <T> memoize(loader: () -> T): () -> T {
    val cache = Suppliers.memoizeWithExpiration(
        loader,
        5, TimeUnit.MINUTES
    )
    return {
        cache.get()
    }
}

fun mpForConstituency(contacts: () -> List<ConstituencyContact>): (ConstituencyName) -> MP {

    val cache = memoize {
        contacts().associateBy { it.constituency }
    }

    return { name -> cache()[name]?.mp ?: throw Defect("We don't have the MP for $name") }
}


fun main() {

    val isDevelopment =
        EnvironmentKey.boolean().required("DEVELOPMENT_MODE", "Use fake data server (local files) & hot reload")
    val dataServiceUri = EnvironmentKey.uri().required("DATA_SERVICE_URI", "URI for Data Service")
    val debugging = EnvironmentKey.boolean().required("DEBUG_MODE", "Print all request and response")
    val port = EnvironmentKey.int().required("PORT", "Listen Port")
    val hotReloadingTemplates = EnvironmentKey.boolean().required("HOT_TEMPLATES")

    val defaultConfig = Environment.defaults(
        isDevelopment of false,
        debugging of false,
        dataServiceUri of Uri.of("http://data"),
        port of 80,
        hotReloadingTemplates of false
    )

    val environment = Environment.JVM_PROPERTIES overrides
            Environment.ENV overrides
            defaultConfig

    val isDevelopmentEnvironment = isDevelopment(environment)

    val events =
        EventFilters.AddTimestamp(Clock.systemUTC())
            .then(EventFilters.AddEventName())
            .then(EventFilters.AddZipkinTraces())
            .then(EventFilters.AddServiceName("pages"))
            .then(AutoMarshallingEvents(Jackson))


    val renderer = Resources.templates(
        templates = TotpHandlebars.templates(),
        hotReload = hotReloadingTemplates(environment)
    )

    val inboundFilters = StandardFilters.incoming(events, debugging(environment))
    val outboundFilters = StandardFilters.outgoing(events)

    val dataClient = if (isDevelopmentEnvironment) {
        outboundFilters.then(static(ResourceLoader.Directory("services/data/datafiles")))
    } else {
        SetBaseUriFrom(Uri.of("/data"))
            .then(ClientFilters.SetHostFrom(dataServiceUri(environment)))
            .then(outboundFilters)
            .then(OkHttp())
    }

    val data2022 = SetBaseUriFrom(Uri.of("/v1/2022")).then(EnsureSuccessfulResponse()).then(dataClient)

    val mediaAppearances = MediaAppearances(dataClient)
    val waterCompanies = WaterCompanies(dataClient)
    val constituencyContacts = memoize(ConstituencyContacts(data2022))
    val allSpills = memoize(AllSpills(data2022))
    val riverRankings = memoize(RiverRankings(data2022))
    val beachRankings = memoize(BathingRankings(data2022))

    val constituencyRankings = memoize(ConstituencyRankings(data2022))

    val mpFor = mpForConstituency(constituencyContacts)

    val constituencyBoundaries = ConstituencyBoundaries(
        SetBaseUriFrom(Uri.of("/constituencies")).then(dataClient)
    )

    val beachBoundaries = BeachBoundaries(
        SetBaseUriFrom(Uri.of("/beaches")).then(dataClient)
    )

    val constituencyRank = { wanted: ConstituencyName ->
        constituencyRankings().firstOrNull { it.constituencyName == wanted }
    }

    val server = Undertow(port = port(environment)).toServer(

        routes(
            "/" bind Method.GET to inboundFilters.then(
                HtmlPageErrorFilter(events, renderer)
                    .then(
                        routes(
                            "/" bind HomepageHandler(
                                renderer = renderer,
                                constituencyRankings = constituencyRankings,
                                bathingRankings = beachRankings,
                                riverRankings = riverRankings,
                                appearances = mediaAppearances,
                                companies = waterCompanies,
                                mpFor = mpFor,
                            ),
                            "/media" bind MediaPageHandler(
                                renderer = renderer,
                                appearances = mediaAppearances
                            ),
                            "/constituencies" bind ConstituenciesPageHandler(
                                renderer = renderer,
                                constituencyRankings = constituencyRankings,
                                mpFor = mpFor,
                            ),
                            "/beaches" bind BeachesPageHandler(
                                renderer = renderer,
                                bathingRankings = beachRankings
                            ),
                            "/beach/{bathing}" bind BathingPageHandler(
                                renderer = renderer,
                                bathingRankings = beachRankings,
                                bathingCSOs = { wanted ->
                                    BathingCSOs(data2022)().filter { wanted == it.bathing.toSlug() }
                                },
                                beachBoundaries = beachBoundaries,
                                mpFor = mpFor,
                                constituencyRank = constituencyRank
                            ),
                            "/rivers" bind RiversPageHandler(
                                renderer = renderer,
                                riverRankings = riverRankings
                            ),
                            "/waterway/{company}/{waterway}" bind WaterwayPageHandler(
                                renderer = renderer,
                                waterwaySpills = waterwayCSOs(allSpills),
                                mpFor = mpFor,
                                constituencyRank = constituencyRank
                            ),
                            "/constituency/{constituency}" bind ConstituencyPageHandler(
                                renderer = renderer,
                                constituencySpills = constituencyCSOs(allSpills),
                                constituencyBoundary = constituencyBoundaries,
                                constituencyLiveData = ConstituencyLiveDataLoader(dataClient),
                                constituencyLiveAvailable = ConstituencyLiveAvailability(dataClient),
                                mpFor = mpFor,
                                constituencyNeighbours = ConstituencyNeighbours(data2022),
                                constituencyRank = constituencyRank,
                                constituencyRivers = constituencyRivers(allSpills, riverRankings),
                            ),
                            "/company/{company}" bind CompanyPageHandler(
                                renderer = renderer,
                                companySummaries = CompanyAnnualSummaries(data2022),
                                waterCompanies = waterCompanies,
                                riverRankings = riverRankings,
                                bathingRankings = beachRankings
                            ),
                            "/map.html" bind OldMapRedirectHandler(),
                            "/sitemap.xml" bind SitemapHandler(
                                renderer = renderer,
                                siteBaseUri = Uri.of("https://top-of-the-poops.org"),
                                uris = SitemapUris(
                                    constituencies = constituencyRankings,
                                    riverRankings = riverRankings
                                )
                            ),
                            "/private/badges/constituencies" bind BadgesConstituenciesHandler(
                                renderer = renderer,
                                constituencyRankings = constituencyRankings,
                                mpFor = mpFor,
                                constituencyBoundaries = constituencyBoundaries,
                            ),
                            "/private/badges/companies" bind BadgesCompaniesHandler(
                                renderer = renderer,
                                companySummaries = CompanyAnnualSummaries(data2022),
                            ),
                            "/private/badges/home" bind BadgesHomeHandler(
                                renderer = renderer,
                                constituencyRankings = constituencyRankings,
                                bathingRankings = beachRankings,
                            ),
                            "/private/badges/rivers" bind BadgesRiversHandler(
                                renderer = renderer,
                                riverRankings = riverRankings,
                            )
                        )
                    )
            ),
            "/data" bind static(ResourceLoader.Directory("services/data/datafiles")),
            "/assets" bind static(Resources.assets(isDevelopmentEnvironment)),
        )
    )

    server.start()
    events(ServerStartedEvent(Uri.of("http://localhost:" + server.port()), isDevelopmentEnvironment))
    server.block()
}