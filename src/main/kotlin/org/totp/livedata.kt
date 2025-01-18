package org.totp

import dev.forkhandles.values.LongValue
import dev.forkhandles.values.LongValueFactory
import dev.forkhandles.values.minValue
import org.http4k.core.*
import org.http4k.lens.Path
import org.http4k.lens.location
import org.http4k.lens.value
import org.totp.db.DurationUnit
import org.totp.db.EnvironmentAgency
import org.totp.db.StreamData
import org.totp.db.ThamesWater
import org.totp.model.data.ConstituencySlug
import org.totp.model.data.TotpJson
import org.totp.pages.slugToConstituency
import java.time.*

class StreamOverflowing(val clock: Clock, val streamData: StreamData) : HttpHandler {

    val response = TotpJson.autoBody<List<StreamData.StreamCSOLiveOverflow>>().toLens()

    override fun invoke(request: Request): Response {
        return Response(Status.OK).with(response of streamData.overflowingAt(clock.instant()))
    }
}

class TimestampMillis private constructor(value: Long) : LongValue(value) {
    fun toInstant(): Instant = Instant.ofEpochMilli(value)

    companion object : LongValueFactory<TimestampMillis>(::TimestampMillis, 0L.minValue) {
        fun of(value: Instant) = TimestampMillis(value.toEpochMilli())
    }
}

class StreamOverflowingByDate(val streamData: StreamData) : HttpHandler {

    val date = Path.value(TimestampMillis).of("date")

    val response = TotpJson.autoBody<List<StreamData.StreamCSOLiveOverflow>>().toLens()

    override fun invoke(request: Request): Response {

        val instant = date(request).toInstant()

        val truncated = instant.truncatedTo(DurationUnit.ofMinutes(5))

        return if ( instant != truncated ) {
            Response(Status.PERMANENT_REDIRECT).location(Uri.of(truncated.toEpochMilli().toString()))
        } else {
            Response(Status.OK).with(response of streamData.overflowingAt(truncated))
        }
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