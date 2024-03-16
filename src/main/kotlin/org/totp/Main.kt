package org.totp

import com.google.common.base.Suppliers
import org.http4k.client.OkHttp
import org.http4k.cloudnative.env.Environment
import org.http4k.cloudnative.env.EnvironmentKey
import org.http4k.core.*
import org.http4k.events.AutoMarshallingEvents
import org.http4k.events.EventFilters
import org.http4k.events.then
import org.http4k.filter.ClientFilters
import org.http4k.filter.ClientFilters.SetBaseUriFrom
import org.http4k.format.Jackson
import org.http4k.lens.*
import org.http4k.lens.Header.LOCATION
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.Undertow
import org.http4k.template.TemplateRenderer
import org.totp.db.*
import org.totp.events.ServerStartedEvent
import org.totp.extensions.Defect
import org.totp.http4k.StandardFilters
import org.totp.model.TotpHandlebars
import org.totp.model.data.*
import org.totp.pages.*
import java.time.Clock
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.LogManager


object Resources {

    private val resourceBase = java.nio.file.Path.of("src/main/resources")

    //not very keen on dev mode, but good enough for now.
    fun templates(devMode: Boolean): TemplateRenderer {

        val templates = TotpHandlebars.templates()

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

object OldMapRedirectHandler {
    operator fun invoke(): HttpHandler {

        val constituency = Query.value(ConstituencyName).optional("c")

        return { request ->
            val selected = constituency(request)

            if (selected != null && constituencyNames.contains(selected)) {
                val slug = selected.toSlug()
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



//   {
//    "date": "2022-12-01",
//    "edm_count": 444,
//    "overflowing": 0,
//    "offline": 1
//  },
class ThamesWaterSummary(val thamesWater: ThamesWater): HttpHandler {

    val response = TotpJson.autoBody<List<ThamesWater.DatedOverflow>>().toLens()

    override fun invoke(request: Request): Response {
        return Response(Status.OK).with(response of thamesWater.infrastructureSummary())
    }
}

fun main() {

    val isDevelopment =
        EnvironmentKey.boolean().required("DEVELOPMENT_MODE", "Use fake data server (local files) & hot reload")
    val dataServiceUri = EnvironmentKey.uri().required("DATA_SERVICE_URI", "URI for Data Service")
    val pollutionServiceUri = EnvironmentKey.uri().required("POLLUTION_SERVICE_URI", "URI for Pollution Service")
    val debugging = EnvironmentKey.boolean().required("DEBUG_MODE", "Print all request and response")
    val dbHost = EnvironmentKey.string().defaulted("DB_HOST", "localhost", "Print all request and response")
    val port = EnvironmentKey.int().required("PORT", "Listen Port")

    val defaultConfig = Environment.defaults(
        isDevelopment of false,
        debugging of false,
        dataServiceUri of Uri.of("http://data"),
        pollutionServiceUri of Uri.of("http://pollution"),
        port of 80,
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

    val renderer = Resources.templates(devMode = isDevelopmentEnvironment)

    val inboundFilters = StandardFilters.incoming(events, debugging(environment))
    val outboundFilters = StandardFilters.outgoing(events)

    val outboundHttp = outboundFilters.then(OkHttp())

    val dataClient = if (isDevelopmentEnvironment) {
        outboundFilters.then(static(ResourceLoader.Directory("services/data/datafiles")))
    } else {
        SetBaseUriFrom(Uri.of("/data"))
            .then(ClientFilters.SetHostFrom(dataServiceUri(environment)))
            .then(outboundHttp)
    }

    val connection = EventsWithConnection(
        Clock.systemUTC(),
        events,
        HikariWithConnection(lazy { datasource(dbHost(environment)) })
    )

    val referenceData = ReferenceData(connection)

    val data2022 = SetBaseUriFrom(Uri.of("/v1/2022")).then(EnsureSuccessfulResponse()).then(dataClient)

    val mediaAppearances = memoize(MediaAppearances(dataClient))
    val waterCompanies = memoize(WaterCompanies(dataClient))
    val allSpills = memoize(AllSpills(data2022))
    val riverRankings = memoize(RiverRankings(data2022))
    val beachRankings = memoize(BathingRankings(data2022))
    val shellfishRankings = memoize(ShellfishRankings(data2022))
    val constituencyRankings = memoize(ConstituencyRankings(data2022))

    val mpFor = mpForConstituency(referenceData::mps)

    val constituencyBoundaries = ConstituencyBoundaries(
        Boundaries(SetBaseUriFrom(Uri.of("/constituencies")).then(dataClient))
    )

    val beachBoundaries = BeachBoundaries(
        Boundaries(SetBaseUriFrom(Uri.of("/beaches")).then(dataClient))
    )

    val shellfisheryBoundaries = ShellfishBoundaries(
        Boundaries(SetBaseUriFrom(Uri.of("/shellfisheries")).then(dataClient))
    )

    val constituencyRank = { wanted: ConstituencyName ->
        constituencyRankings().firstOrNull { it.constituencyName == wanted }
    }

    val thamesWater = ThamesWater(connection)

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
                                shellfishRankings = shellfishRankings,
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
                                constituencyLiveAvailable = memoize(thamesWater::haveLiveDataFor),
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
                                bathingRankings = beachRankings,
                                csoTotals = allSpills,
                                companyLiveDataAvailable =
                                { name ->
                                    val uri = Uri.of("/v1/2022/spills-live-summary-${name.toSlug()}.json")
                                    val response = dataClient(Request(Method.GET, uri))
                                    response.status.successful
                                },
                            ),
                            "/shellfisheries" bind ShellfisheriesPageHandler(
                                renderer = renderer,
                                shellfishRankings = shellfishRankings
                            ),
                            "/shellfishery/{area}" bind ShellfisheryPageHandler(
                                renderer = renderer,
                                shellfishRankings = shellfishRankings,
                                shellfishSpills = { wanted ->
                                    ShellfishCSOs(data2022)().filter { wanted == it.shellfishery.toSlug() }
                                },
                                mpFor = mpFor,
                                constituencyRank = constituencyRank,
                                shellfisheryBoundaries = shellfisheryBoundaries
                            ),
                            "/live/thames-water/overflow-summary" bind ThamesWaterSummary(
                                thamesWater
                            ),
                            "/map.html" bind OldMapRedirectHandler(),
                            "/sitemap.xml" bind SitemapHandler(
                                siteBaseUri = Uri.of("https://top-of-the-poops.org"),
                                uris = SitemapUris(
                                    constituencies = constituencyRankings,
                                    riverRankings = riverRankings,
                                    beachRankings = beachRankings,
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

    silenceUndertowLogging()

    server.start()
    events(ServerStartedEvent(Uri.of("http://localhost:" + server.port()), isDevelopmentEnvironment))
    server.block()
}

fun silenceUndertowLogging() {
    LogManager.getLogManager().getLogger("").level = Level.WARNING
}

