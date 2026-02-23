package org.totp.pages

import org.http4k.core.*
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.value
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.db.AnnualSewageRainfall
import org.totp.db.DailySewageRainfall
import org.totp.db.StreamData
import org.totp.db.StreamId
import org.totp.http4k.pageUriFrom
import org.totp.http4k.removeQuery
import org.totp.model.PageViewModel
import org.totp.model.data.*
import java.text.NumberFormat
import java.time.*
import java.time.format.TextStyle
import java.util.*

data class RenderableStreamCsoSummary(
    val company: RenderableCompany,
    val id: RenderableStreamId,
    val site_name: String,
    val receiving_water: String,
    val location: Coordinates,
    val start: RenderableDuration,
    val offline: RenderableDuration,
    val potential: RenderableDuration,
    val days: RenderableCount,
    val constituency: RenderableConstituency,
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
    val availableYears: List<RenderableLiveYear>,
    val selectedYear: RenderableLiveYear,
    val annual: RenderableAnnualSewageRainfall,
) : PageViewModel(pageUri)

data class RenderableDailySewageRainfall(
    val date: LocalDate,
    val duration: RenderableDuration,
    val count: RenderableCount,
    val rainfall: Double,
    val sewageClass: String,
    val rainfallClass: String
)

data class RenderableMonthlySewageRainfall(val month: String, val days: List<RenderableDailySewageRainfall>) {
    val duration = RenderableDuration(days.fold(Duration.ZERO) { acc, item -> acc + item.duration.value })
    val count = RenderableCount(days.sumOf { it.count.count })
}

data class RenderableAnnualSewageRainfall(
    val year: Int,
    val months: List<RenderableMonthlySewageRainfall>
) {
    val duration = RenderableDuration(months.fold(Duration.ZERO) { acc, item -> acc + item.duration.value })
    val count = RenderableCount(months.sumOf { it.count.count })
}

fun AnnualSewageRainfall.toRenderable(): RenderableAnnualSewageRainfall {
    return RenderableAnnualSewageRainfall(
        year = this.year,
        months = this.months.map { m ->
            RenderableMonthlySewageRainfall(
                m.month.getDisplayName(TextStyle.SHORT, Locale.UK),
                days = m.days.map { d ->
                    RenderableDailySewageRainfall(
                        date = d.date,
                        duration = RenderableDuration(d.duration),
                        count = RenderableCount(d.count),
                        rainfall = d.rainfall,
                        sewageClass = calculateSewageClass(d),
                        rainfallClass = ((d.rainfall / 25).coerceIn(0.0, 1.0) * 10).toInt().let {"rainfall-${it}"}
                    )
                }
            )
        }
    )
}

private fun calculateSewageClass(d: DailySewageRainfall): String {
    val scaled = ((d.duration.toMillis().toDouble() / Duration.ofHours(24).toMillis()).coerceIn(0.0, 1.0) * 6).toInt()
    val index = if (d.duration > Duration.ofMinutes(10)) scaled.coerceAtLeast(1) else scaled
    return "sewage-${index}"
}

class ConstituencyLiveNotAvailablePage(
    pageUri: Uri,
    val constituency: RenderableConstituency,
    val geojson: GeoJSON,
    val neighbours: List<RenderableConstituency>,
    val constituencies: List<RenderableConstituency>,
) : PageViewModel(pageUri)

data class RenderableLiveYear(
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
        annualSewageRainfall: (ConstituencyName, LocalDate, LocalDate) -> AnnualSewageRainfall,
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

                val today = LocalDate.ofInstant(clock.instant(), ZoneId.of("UTC"))

                val currentYear = today.year
                val selectedYear = yearParam(request) ?: currentYear

                val availableYears = selectedLiveYears(thisPageUri, currentYear, selectedYear)

                if (liveAvailable.contains(constituencyName)) {
                    val startDate = LocalDate.of(selectedYear, 1, 1)
                    val endDate = if ( selectedYear == currentYear ) {
                        today.withDayOfMonth(1).plusMonths(1)
                    } else {
                        LocalDate.of(selectedYear + 1, 1, 1)
                    }
                    val totals = constituencyLiveTotals(constituencyName, startDate, endDate)
                    val hours = totals.duration.toHours()
                    val formatted = NumberFormat.getNumberInstance().format(hours)

                    Response(Status.OK).with(
                        viewLens of ConstituencyLivePage(
                            thisPageUri,
                            selectedYear = RenderableLiveYear(
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
                            annual = annualSewageRainfall(constituencyName, startDate, endDate).toRenderable(),
                            csos = csoLive(constituencyName, startDate, endDate)
                                .map { it.toRenderable() }
                                .sortedByDescending { it.start.value },
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

fun selectedLiveYears(
    thisPageUri: Uri,
    currentYear: Int,
    selectedYear: Int
): List<RenderableLiveYear> {
    val availableYears = (2025..currentYear).map {
        RenderableLiveYear(
            uri = if (it == currentYear) {
                thisPageUri.removeQuery("year")
            } else {
                thisPageUri.removeQuery("year").query("year", it.toString())
            },
            current = it == selectedYear,
            year = it
        )
    }
    return availableYears
}

data class RenderableStreamId(val id: String, val uri: Uri)

fun StreamData.StreamCsoSummary.toRenderable(): RenderableStreamCsoSummary {
    return RenderableStreamCsoSummary(
        company = company.toRenderable(),
        constituency = pcon24nm.toRenderable(linkLive = true),
        id = id.toRenderable(),
        site_name = site_name.value,
        receiving_water = receiving_water.value,
        location = location,
        start = RenderableDuration(start),
        offline = RenderableDuration(offline),
        potential = RenderableDuration(potential),
        days = RenderableCount(days),
    )
}

fun StreamId.toRenderable(year: Int? = null): RenderableStreamId {
    val base = Uri.of("/overflow/${value}")

    val uri = if (year != null) {
        base.query("year", year.toString())
    } else {
        base
    }

    return RenderableStreamId(value, uri)
}
