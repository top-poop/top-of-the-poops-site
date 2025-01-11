package org.totp

import org.http4k.core.*
import org.http4k.lens.Path
import org.http4k.lens.value
import org.totp.db.EnvironmentAgency
import org.totp.db.StreamData
import org.totp.db.ThamesWater
import org.totp.model.data.ConstituencySlug
import org.totp.model.data.TotpJson
import org.totp.pages.slugToConstituency
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class StreamOverflowing(val streamData: StreamData) : HttpHandler {

    val response = TotpJson.autoBody<List<StreamData.StreamCSOLiveOverflow>>().toLens()

    override fun invoke(request: Request): Response {
        return Response(Status.OK).with(response of streamData.overflowingRightNow())
    }
}

class ThamesWaterSummary(val thamesWater: ThamesWater) : HttpHandler {

    val response = TotpJson.autoBody<List<ThamesWater.DatedOverflow>>().toLens()

    override fun invoke(request: Request): Response {
        return Response(Status.OK).with(response of thamesWater.infrastructureSummary())
    }
}

class ThamesWaterPermitEvents(val clock: Clock, val thamesWater: ThamesWater) : HttpHandler {

    val response = TotpJson.autoBody<List<ThamesWater.Thing>>().toLens()
    val permit = Path.of("permit")

    override fun invoke(request: Request): Response {
        val now = clock.instant()
        val events = thamesWater.eventSummaryForCSO(
            permit_id = permit(request),
            startDate = LocalDate.ofInstant(now.minus(Duration.ofDays(90)), ZoneId.of("UTC")),
            endDate = LocalDate.ofInstant(
                now, ZoneId.of("UTC")
            )
        )
        return when {
            events.isEmpty() -> Response(Status.NOT_FOUND)
            else -> Response(Status.OK).with(
                response of events
            )
        }
    }
}

class ThamesWaterConstituencyEvents(val clock: Clock, val thamesWater: ThamesWater) : HttpHandler {

    val response = TotpJson.autoBody<List<ThamesWater.Thing>>().toLens()
    val constituency = Path.value(ConstituencySlug).of("constituency")

    override fun invoke(request: Request): Response {
        val now = clock.instant()

        val constituencyName = slugToConstituency[constituency(request)] ?: return Response(Status.NOT_FOUND)

        val events = thamesWater.eventSummaryForConstituency(
            constituencyName = constituencyName,
            startDate = LocalDate.parse("2023-01-01"),
            endDate = LocalDate.ofInstant(now, ZoneId.of("UTC"))
        )
        return when {
            events.isEmpty() -> Response(Status.NOT_FOUND)
            else -> Response(Status.OK).with(
                response of events
            )
        }
    }
}

class EnvironmentAgencyRainfall(val clock: Clock, val environmentAgency: EnvironmentAgency) : HttpHandler {

    val response = TotpJson.autoBody<List<EnvironmentAgency.Rainfall>>().toLens()
    val constituency = Path.value(ConstituencySlug).of("constituency")

    override fun invoke(request: Request): Response {
        val now = clock.instant()

        val constituencyName = slugToConstituency[constituency(request)] ?: return Response(Status.NOT_FOUND)

        val events = environmentAgency.rainfallForConstituency(
            constituencyName = constituencyName,
            startDate = LocalDate.parse("2023-01-01"),
            endDate = LocalDate.ofInstant(now, ZoneId.of("UTC"))
        )
        return when {
            events.isEmpty() -> Response(Status.NOT_FOUND)
            else -> Response(Status.OK).with(
                response of events
            )
        }
    }
}