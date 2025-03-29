package org.totp

import dev.forkhandles.values.LongValue
import dev.forkhandles.values.LongValueFactory
import dev.forkhandles.values.minValue
import org.http4k.core.*
import org.http4k.filter.MaxAgeTtl
import org.http4k.lens.*
import org.totp.db.DurationUnit
import org.totp.db.EnvironmentAgency
import org.totp.db.StreamData
import org.totp.db.ThamesWater
import org.totp.model.data.ConstituencySlug
import org.totp.model.data.TotpJson
import org.totp.pages.slugToConstituency
import java.time.*

class TimestampMillis private constructor(value: Long) : LongValue(value) {
    fun toInstant(): Instant = Instant.ofEpochMilli(value)

    companion object : LongValueFactory<TimestampMillis>(::TimestampMillis, 0L.minValue) {
        fun of(value: Instant) = TimestampMillis(value.toEpochMilli())
    }
}

class StreamOverflowingByDate(val clock: Clock, val streamData: StreamData) : HttpHandler {
    val cache = Header.required("Cache-Control")

    val epochms = Path.value(TimestampMillis).of("epochms")

    val response = TotpJson.autoBody<List<StreamData.StreamCSOLiveOverflow>>().toLens()

    val haveDataDuration = Duration.ofHours(3)

    override fun invoke(request: Request): Response {

        val instant = epochms(request).toInstant()

        val truncated = instant.truncatedTo(DurationUnit.ofMinutes(5))

        val maxAge = if (instant.isBefore(clock.instant().minus(haveDataDuration))) {
            MaxAgeTtl(Duration.ofDays(1))
        } else {
            MaxAgeTtl(Duration.ofHours(1))
        }

        return if (instant != truncated) {
            Response(Status.PERMANENT_REDIRECT).location(Uri.of(truncated.toEpochMilli().toString()))
        } else {
            Response(Status.OK).with(
                response of streamData.overflowingAt(truncated),
                cache of listOf("public", maxAge.toHeaderValue()).joinToString(",")
            )
        }
    }
}

class ThamesWaterSummary(val thamesWater: ThamesWater) : HttpHandler {

    val response = TotpJson.autoBody<List<ThamesWater.DatedOverflow>>().toLens()

    override fun invoke(request: Request): Response {
        return Response(Status.OK).with(response of thamesWater.infrastructureSummary())
    }
}

private val THAMES_WATER_LIVE_DATA_START = LocalDate.parse("2023-01-01")

class ThamesWaterConstituencyEvents(val clock: Clock, val thamesWater: ThamesWater) : HttpHandler {

    val response = TotpJson.autoBody<List<ThamesWater.Thing>>().toLens()
    val constituency = Path.value(ConstituencySlug).of("constituency")
    val sinceDate = Query.localDate().optional("since")

    override fun invoke(request: Request): Response {
        val now = clock.instant()
        val since = sinceDate(request) ?: THAMES_WATER_LIVE_DATA_START

        val constituencyName = slugToConstituency[constituency(request)] ?: return Response(Status.NOT_FOUND)

        val today = LocalDate.ofInstant(now, ZoneId.of("UTC"))
        val events = thamesWater.eventSummaryForConstituency(
            constituencyName = constituencyName,
            startDate = since,
            endDate = today
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
    val sinceDate = Query.localDate().optional("since")

    override fun invoke(request: Request): Response {
        val now = clock.instant()
        val since = sinceDate(request) ?: THAMES_WATER_LIVE_DATA_START

        val constituencyName = slugToConstituency[constituency(request)] ?: return Response(Status.NOT_FOUND)

        val events = environmentAgency.rainfallForConstituency(
            constituencyName = constituencyName,
            startDate = since,
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

class EnvironmentAgencyGrid(val clock: Clock, val environmentAgency: EnvironmentAgency) : HttpHandler {

    val response = TotpJson.autoBody<List<EnvironmentAgency.RainfallGrid>>().toLens()
    val cache = Header.required("Cache-Control")
    val epochms = Path.value(TimestampMillis).of("epochms")

    val haveDataDuration = Duration.ofDays(3)

    override fun invoke(request: Request): Response {
        val requestedInstant = epochms(request).toInstant()
        val truncated = requestedInstant.truncatedTo(DurationUnit.ofMinutes(15))

        return if (requestedInstant != truncated) {
            Response(Status.PERMANENT_REDIRECT).location(Uri.of(truncated.toEpochMilli().toString()))
        } else {
            val dataProbablyAvailableDate = clock.instant().minus(haveDataDuration)

            val grid = if (requestedInstant.isAfter(dataProbablyAvailableDate)) {
                emptyList()
            } else {
                environmentAgency.rainfallGridAt(truncated)
            }

            val maxAge = if (grid.isEmpty()) {
                MaxAgeTtl(Duration.ofHours(1))
            } else {
                MaxAgeTtl(Duration.ofDays(1))
            }

            Response(Status.OK).with(
                response of grid,
                cache of listOf("public", maxAge.toHeaderValue()).joinToString(",")
            )
        }
    }
}