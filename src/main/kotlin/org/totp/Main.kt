package org.totp

import com.google.common.base.Suppliers
import org.http4k.client.OkHttp
import org.http4k.config.Environment
import org.http4k.config.EnvironmentKey
import org.http4k.core.*
import org.http4k.events.AutoMarshallingEvents
import org.http4k.events.EventFilters
import org.http4k.events.then
import org.http4k.filter.*
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
import java.time.Duration
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

class EDMAnnualSummary(val edm: EDM) : HttpHandler {

    val data = TotpJson.autoBody<List<EDM.ConstituencyAnnualSummary>>().toLens()
    val constituency = Path.value(ConstituencySlug).of("constituency")
    override fun invoke(request: Request): Response {

        val constituencyName = slugToConstituency[constituency(request)]

        return if (constituencyName == null) {
            Response(Status.NOT_FOUND)
        } else {
            val annual = edm.annualSummariesForConstituency(constituencyName)
            Response(Status.OK).with(data of annual)
        }
    }
}

fun main() {

    val isDevelopment =
        EnvironmentKey.boolean().required("DEVELOPMENT_MODE", "Use fake data server (local files) & hot reload")
    val dataServiceUri = EnvironmentKey.uri().required("DATA_SERVICE_URI", "URI for Data Service")
    val debugging = EnvironmentKey.boolean().required("DEBUG_MODE", "Print all request and response")
    val dbHost = EnvironmentKey.string().defaulted("DB_HOST", "localhost", "Print all request and response")
    val port = EnvironmentKey.int().required("PORT", "Listen Port")

    val defaultConfig = Environment.defaults(
        isDevelopment of false,
        debugging of false,
        dataServiceUri of Uri.of("http://data"),
        port of 80,
    )

    val environment = Environment.JVM_PROPERTIES overrides
            Environment.ENV overrides
            defaultConfig

    val isDevelopmentEnvironment = isDevelopment(environment)

    val clock = Clock.systemUTC()

    val events =
        EventFilters.AddTimestamp(clock)
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
        clock,
        events,
        HikariWithConnection(lazy { datasource(dbHost(environment)) })
    )

    val referenceData = ReferenceData(connection)

    val data2023 = SetBaseUriFrom(Uri.of("/v1/2023")).then(EnsureSuccessfulResponse()).then(dataClient)

    val mediaAppearances = memoize(MediaAppearances(dataClient))
    val waterCompanies = memoize(WaterCompanies(dataClient))
    val allSpills = memoize(AllSpills(data2023))
    val riverRankings = memoize(RiverRankings(data2023))
    val beachRankings = memoize(BathingRankings(data2023))
    val shellfishRankings = memoize(ShellfishRankings(data2023))
    val constituencyRankings = memoize(ConstituencyRankings(data2023))

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

    val stream = StreamData(connection)
    val environmentAgency = EnvironmentAgency(connection)

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
                            "/now" bind NowHandler(
                                renderer = renderer,
                                streamData = stream
                            ),
                            "/support" bind SupportUsHandler(renderer = renderer),
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
                                    BathingCSOs(data2023)().filter { wanted == it.bathing.toSlug() }
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
                                clock = clock,
                                renderer = renderer,
                                constituencySpills = constituencyCSOs(allSpills),
                                constituencyBoundary = constituencyBoundaries,
                                constituencyLiveAvailable = memoize(thamesWater::haveLiveDataFor),
                                mpFor = mpFor,
                                constituencyNeighbours = ConstituencyNeighbours(data2023),
                                constituencyRank = constituencyRank,
                                constituencyRivers = constituencyRivers(allSpills, riverRankings),
                            ),
                            "/company/{company}" bind CompanyPageHandler(
                                renderer = renderer,
                                companySummaries = CompanyAnnualSummaries(data2023),
                                waterCompanies = waterCompanies,
                                riverRankings = riverRankings,
                                bathingRankings = beachRankings,
                                csoTotals = allSpills,
                                companyLivedata =
                                    { name ->
                                        when (name) {
                                            CompanyName("Thames Water") -> CSOLiveData(thamesWater.overflowingRightNow())
                                            else -> null
                                        }
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
                                    ShellfishCSOs(data2023)().filter { wanted == it.shellfishery.toSlug() }
                                },
                                mpFor = mpFor,
                                constituencyRank = constituencyRank,
                                shellfisheryBoundaries = shellfisheryBoundaries
                            ),
                            "/private/badges" bind routes(
                                "/constituencies/{letter}" bind BadgesConstituenciesHandler(
                                    renderer = renderer,
                                    constituencyRankings = constituencyRankings,
                                    mpFor = mpFor,
                                    constituencyBoundaries = constituencyBoundaries,
                                ),
                                "/companies" bind BadgesCompaniesHandler(
                                    renderer = renderer,
                                    companySummaries = CompanyAnnualSummaries(data2023),
                                ),
                                "/home" bind BadgesHomeHandler(
                                    renderer = renderer,
                                    constituencyRankings = constituencyRankings,
                                    bathingRankings = beachRankings,
                                ),
                                "/rivers" bind BadgesRiversHandler(
                                    renderer = renderer,
                                    riverRankings = riverRankings,
                                ),
                            ),
                            "/fragments" bind routes(
                                "/stream/table/" bind { Response(Status.OK) }
                            )
                        )
                    )
            ),
            "/live" bind routes(
                "/stream" bind routes(
                    "/overflowing/{epochms}" bind StreamOverflowingByDate(clock, stream),
                ),
                "/thames-water" bind routes(
                    "/overflow-summary" bind ThamesWaterSummary(thamesWater),
                    "/events/constituency/{constituency}" bind ThamesWaterConstituencyEvents(clock, thamesWater),
                ),
                "/environment-agency" bind routes(
                    "/rainfall/{constituency}" bind EnvironmentAgencyRainfall(clock, environmentAgency),
                    "/rainfall/grid/{epochms}" bind EnvironmentAgencyGrid(clock, environmentAgency)
                ),
            ).withFilter(
                CachingFilters.CacheResponse.FallbackCacheControl(
                    defaultCacheTimings = DefaultCacheTimings(
                        maxAge = MaxAgeTtl(Duration.ofMinutes(30)),
                        staleIfErrorTtl = StaleIfErrorTtl(Duration.ofMinutes(60)),
                        staleWhenRevalidateTtl = StaleWhenRevalidateTtl(Duration.ofMinutes(60))
                    )
                )
            ),
            "/data-new/constituency/{constituency}/annual-pollution" bind EDMAnnualSummary(EDM(connection)),
            "/map.html" bind OldMapRedirectHandler(),
            "/sitemap.xml" bind SitemapHandler(
                siteBaseUri = Uri.of("https://top-of-the-poops.org"),
                uris = SitemapUris(
                    constituencies = constituencyRankings,
                    riverRankings = riverRankings,
                    beachRankings = beachRankings,
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

