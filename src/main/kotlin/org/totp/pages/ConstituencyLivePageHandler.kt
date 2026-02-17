package org.totp.pages

import org.http4k.core.*
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.value
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.db.StreamData
import org.totp.http4k.pageUriFrom
import org.totp.http4k.removeQuery
import org.totp.model.PageViewModel
import org.totp.model.data.*
import java.text.NumberFormat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class RenderableStreamCsoSummary(
    val company: RenderableCompany,
    val id: String,
    val site_name: String,
    val receiving_water: String,
    val location: Coordinates,
    val duration: RenderableDuration,
    val days: RenderableCount,
)

class ConstituencyLivePage(
    pageUri: Uri,
    val asAt: Instant,
    val constituency: RenderableConstituency,
    val mp: MP?,
    val geojson: GeoJSON,
    val neighbours: List<RenderableConstituency>,
    val constituencies: List<RenderableConstituency>,
    val summary: RenderableConstituencyLiveTotal,
    val share: SocialShare?,
    val csos: List<RenderableStreamCsoSummary>,
    val csoUri: Uri,
    val rainfallUri: Uri,
    val availableYears: List<ConstituencyLiveYear>,
    val selectedYear: ConstituencyLiveYear,
) : PageViewModel(pageUri)

class ConstituencyLiveNotAvailablePage(
    pageUri: Uri,
    val constituency: RenderableConstituency,
    val geojson: GeoJSON,
    val neighbours: List<RenderableConstituency>,
    val constituencies: List<RenderableConstituency>,
) : PageViewModel(pageUri)

data class ConstituencyLiveYear(
    val uri: Uri,
    val current: Boolean,
    val year: Int,
)

object ConstituencyLivePageHandler {
    operator fun invoke(
        clock: Clock,
        renderer: TemplateRenderer,
        constituencyBoundary: (ConstituencyName) -> GeoJSON,
        mpFor: (ConstituencyName) -> MP,
        constituencyLiveAvailable: () -> Set<ConstituencyName>,
        constituencyNeighbours: (ConstituencyName) -> List<ConstituencyName>,
        constituencyLiveTotals: (ConstituencyName, LocalDate, LocalDate) -> StreamData.ConstituencyLiveTotal,
        csoLive: (ConstituencyName, LocalDate, LocalDate) -> List<StreamData.StreamCsoSummary>,
        liveDataLatest: () -> Instant,
    ): HttpHandler {

        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        val slug = Path.value(Slug).of("constituency", "The constituency")

        val yearParam = Query.int().optional("year")

        return { request: Request ->

            val slug = slug(request)
            val liveAvailable = constituencyLiveAvailable()

            slugToConstituency[slug]?.let { constituencyName ->

                val mp = mpFor(constituencyName)

                val neighbourConstituencies = constituencyNeighbours(constituencyName)
                    .sorted()
                    .map { it.toRenderable(linkLive = true) }

                val allConstituencies = slugToConstituency
                    .map {
                        it.value.toRenderable(
                            current = it.key == slug,
                            haveLive = liveAvailable.contains(it.value),
                            linkLive = true,
                        )
                    }

                val thisPageUri = pageUriFrom(request)

                val currentYear = LocalDate.now().year
                val selectedYear = yearParam(request) ?: currentYear

                val availableYears = (2025..currentYear).map {
                    ConstituencyLiveYear(
                        uri = if (it == currentYear) {
                            thisPageUri.removeQuery("year")
                        } else {
                            thisPageUri.removeQuery("year").query("year", it.toString())
                        },
                        current = it == selectedYear,
                        year = it
                    )
                }

                if (liveAvailable.contains(constituencyName)) {

                    val liveDataStart = LocalDate.ofInstant(clock.instant(), ZoneId.of("UTC")).minusMonths(3)

                    val startDate = LocalDate.of(selectedYear, 1, 1)
                    val endDate = LocalDate.of(selectedYear, 12, 31)
                    val totals = constituencyLiveTotals(constituencyName, startDate, endDate)
                    val hours = totals.duration.toHours()
                    val formatted = NumberFormat.getNumberInstance().format(hours)

                    Response(Status.OK).with(
                        viewLens of ConstituencyLivePage(
                            thisPageUri,
                            selectedYear = ConstituencyLiveYear(
                                thisPageUri,
                                current = selectedYear == currentYear,
                                year = selectedYear
                            ),
                            asAt = liveDataLatest(),
                            availableYears = availableYears,
                            constituency = constituencyName.toRenderable(current = true),
                            mp = mp,
                            geojson = constituencyBoundary(constituencyName),
                            neighbours = neighbourConstituencies,
                            constituencies = allConstituencies,
                            summary = totals.toRenderable(),
                            share = SocialShare(
                                uri = thisPageUri,
                                text = if (hours > 100) {
                                    "Unbelievable $formatted hours of sewage in $selectedYear in $constituencyName"
                                } else {
                                    "Unusually, only $formatted hours of sewage in $selectedYear in $constituencyName"
                                },
                                tags = listOf("sewage"),
                                via = "sewageuk",
                                twitterImageUri = Uri.of("https://top-of-the-poops.org/badges/constituency/${slug}-2024.png")
                            ),
                            csos = csoLive(constituencyName, startDate, endDate)
                                .map { it.toRenderable() }
                                .sortedByDescending { it.duration.value },
                            csoUri = Uri.of("/live/stream/events/constituency/$slug")
                                .query("since", liveDataStart.toString()),
                            rainfallUri = Uri.of("/live/environment-agency/rainfall/$slug")
                                .query("since", liveDataStart.toString()),
                        )
                    )
                } else {
                    Response(Status.OK).with(
                        viewLens of ConstituencyLiveNotAvailablePage(
                            thisPageUri.removeQuery(),
                            constituencyName.toRenderable(current = true),
                            geojson = constituencyBoundary(constituencyName),
                            neighbours = neighbourConstituencies,
                            constituencies = allConstituencies,
                        )
                    )
                }

            } ?: Response(Status.NOT_FOUND)
        }
    }
}

fun StreamData.StreamCsoSummary.toRenderable(): RenderableStreamCsoSummary {
    return RenderableStreamCsoSummary(
        company = company.toRenderable(),
        id = id,
        site_name = site_name.value,
        receiving_water = receiving_water.value,
        location = location,
        duration = RenderableDuration(duration),
        days = RenderableCount(days),
    )
}
