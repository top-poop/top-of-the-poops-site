package org.totp.db

import org.totp.model.data.ConstituencyName
import java.time.Duration
import java.time.LocalDate
import java.time.Month

data class DailySewageRainfall(
    val date: LocalDate,
    val start: Duration,
    val offline: Duration,
    val potential: Duration,
    val count: Int,
    val rainfall: Double
)

data class MonthlySewageRainfall(
    val month: Month,
    val days: List<DailySewageRainfall>
)

data class AnnualSewageRainfall(
    val year: Int,
    val months: List<MonthlySewageRainfall>
) {
    fun totalDuration(): Duration = months.flatMap { it.days.map { it.start } }
        .fold(Duration.ZERO) { acc, item -> acc.plus(item) }
}

class AnnualLiveSewage(val environmentAgency: EnvironmentAgency, val streamData: StreamData) {
    fun byConstituency(constituencyName: ConstituencyName, start: LocalDate, end: LocalDate): AnnualSewageRainfall {
        val sewage = streamData.dailyByConstituency(constituencyName, start, end)
        val rainfall = environmentAgency.rainfallForConstituency(constituencyName, start, end)

        return aggregate(sewage, rainfall, start, end)
    }

    fun byCso(
        streamId: StreamId,
        constituencyName: ConstituencyName,
        start: LocalDate,
        end: LocalDate
    ): AnnualSewageRainfall {
        val sewage = streamData.dailyByStreamId(streamId, start, end)
        val rainfall = environmentAgency.rainfallForConstituency(constituencyName, start, end)

        return aggregate(sewage, rainfall, start, end)
    }

    private fun aggregate(
        sewage: List<StreamData.DailySummary>,
        rainfall: List<EnvironmentAgency.Rainfall>,
        start: LocalDate,
        end: LocalDate
    ): AnnualSewageRainfall {
        val sewageByDate = sewage.associateBy { it.date }
        val rainfallByDate = rainfall.associateBy { it.d }

        val monthlyMap = mutableMapOf<Int, MutableMap<Month, MutableList<DailySewageRainfall>>>()

        var current = start
        while (!current.isEqual(end)) {
            val year = current.year
            val month = current.month

            val dailySewage = sewageByDate[current]?.let {
                DailySewageRainfall(
                    current,
                    start = it.start,
                    offline = it.offline,
                    potential = it.potential,
                    0,
                    0.0
                )
            } ?: DailySewageRainfall(
                date = current,
                start = Duration.ZERO,
                offline = Duration.ZERO,
                potential = Duration.ZERO,
                count = 0,
                rainfall = 0.0
            )

            val combined = dailySewage.copy(rainfall = rainfallByDate[current]?.c ?: 0.0)

            val monthsForYear = monthlyMap.getOrPut(year) { mutableMapOf() }
            val daysForMonth = monthsForYear.getOrPut(month) { mutableListOf() }

            daysForMonth.add(combined)

            current = current.plusDays(1)
        }

        return monthlyMap.map { (year, months) ->
            val monthlyList = months.entries.sortedBy { it.key.value }.map { (month, days) ->
                MonthlySewageRainfall(
                    month = month,
                    days = days
                )
            }

            AnnualSewageRainfall(
                year = year,
                months = monthlyList
            )
        }.first()
    }
}