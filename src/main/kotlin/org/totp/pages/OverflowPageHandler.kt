package org.totp.pages

import org.http4k.core.*
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.value
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.db.StreamData
import org.totp.db.StreamId
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class OverflowPage(
    uri: Uri,
    val events: List<StreamData.CSOEvent>,
    val monthly: List<StreamData.DatedBucket>,
    val cso: RenderableStreamCsoSummary,
    val selectedYear: RenderableLiveYear,
    val availableYears: List<RenderableLiveYear>
) :
    PageViewModel(uri)

class OverflowPageHandler(val clock: Clock, val renderer: TemplateRenderer, val stream: StreamData) : HttpHandler {
    val id = Path.value(StreamId).of("id", "Overflow Id (stream)")
    val year = Query.int().optional("year")

    val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

    override fun invoke(request: Request): Response {

        val id = id(request)
        val today = LocalDate.ofInstant(clock.instant(), ZoneId.of("UTC"))
        val currentYear = today.year

        val selectedYear = year(request) ?: currentYear

        val start = LocalDate.of(selectedYear, 1, 1)
        val end = start.plusYears(1)

        val cso = stream.cso(id, start, end)

        val thisPageUri = pageUriFrom(request)

        val availableYears = selectedLiveYears(thisPageUri, currentYear, selectedYear)

        return cso?.let {
            Response(Status.OK).with(
                viewLens of OverflowPage(
                    uri = thisPageUri,
                    cso = cso.toRenderable(),
                    selectedYear = RenderableLiveYear(
                        thisPageUri,
                        current = selectedYear == currentYear,
                        year = selectedYear
                    ),
                    availableYears = availableYears,
                    events = stream.eventsForCso(
                        id,
                        start = start,
                        end = end
                    ),
                    monthly = stream.csoMonthlyBuckets(id, current=today, start, end)
                )
            )
        } ?: Response(Status.NOT_FOUND)
    }
}
