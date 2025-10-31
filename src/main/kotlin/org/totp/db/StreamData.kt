package org.totp.db

import dev.forkhandles.values.StringValue
import dev.forkhandles.values.StringValueFactory
import org.http4k.events.Event
import org.http4k.events.Events
import org.totp.db.NamedQueryBlock.Companion.block
import org.totp.db.ThamesWater.DatedOverflow
import org.totp.model.data.CompanyName
import org.totp.model.data.ConstituencyName
import org.totp.model.data.Coordinates
import org.totp.model.data.StreamCompanyName
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class StreamId(value: String) : StringValue(value) {
    companion object : StringValueFactory<StreamId>(::StreamId)
}

class StreamData(private val events: Events, private val connection: WithConnection) {

    enum class StreamEvent(val dbName: String) {
        Start("Start"),
        Stop("Stop"),
        Unknown("Unknown")
    }

    data class StreamCSOLiveOverflow(
        val id: StreamId,
        val company: CompanyName,
        val pcon24nm: ConstituencyName,
        val started: Instant,
        val loc: Coordinates,
    )

    data class StreamCSOCount(val start: Int, val stop: Int) {
        val total = start + stop
    }

    data class StreamCompanyStatus(val company: CompanyName, val count: StreamCSOCount)

    data class StreamOverflowSummary(
        val count: StreamCSOCount,
        val companies: List<StreamCompanyStatus>
    )

    fun summary(): StreamOverflowSummary {
        val summary = mutableMapOf<String, MutableMap<String, Int>>()
        connection.execute(NamedQueryBlock("stream-overflow-summary") {
            query(
                sql = """
WITH ranked_events AS (
    SELECT
        e.*,
        ROW_NUMBER() OVER (PARTITION BY e.stream_cso_id ORDER BY e.event_time DESC) AS rnk
    FROM
        stream_cso_event as e
)
SELECT m.stream_company, count(*) as count, e.event
FROM stream_cso m
         JOIN ranked_events e ON m.stream_cso_id = e.stream_cso_id AND e.rnk = 1
group by m.stream_company, e.event
order by m.stream_company;                                  
                """.trimIndent(),
                mapper = {
                    val company = it.getString("stream_company")
                    val byCompany = summary.computeIfAbsent(company, { mutableMapOf() })
                    byCompany.put(it.getString("event"), it.getInt("count"))
                }
            )
        })

        val companies = summary.entries.map {
            StreamCompanyStatus(
                company = CompanyName.of(it.key),
                count = StreamCSOCount(
                    start = it.value[StreamEvent.Start.dbName] ?: 0,
                    stop = it.value[StreamEvent.Stop.dbName] ?: 0
                )
            )
        }.toList()

        return StreamOverflowSummary(
            count = StreamCSOCount(
                start = companies.sumOf { it.count.start },
                stop = companies.sumOf { it.count.stop }
            ),
            companies = companies
        )
    }

    fun overflowingAt(instant: Instant): List<StreamCSOLiveOverflow> {
        return connection.execute(NamedQueryBlock("stream-overflowing-at") {
            query(
                sql = """                
WITH ranked_events AS (
    SELECT
        e.*,
        ROW_NUMBER() OVER (PARTITION BY e.stream_cso_id ORDER BY e.event_time DESC, e.update_time DESC) AS rnk
    FROM
        stream_cso_event as e
    where e.event_time <= ?
)
SELECT gr.pcon24nm, m.stream_company, m.stream_id, m.lat, m.lon, e.event, e.event_time, e.update_time
FROM stream_cso m
         join stream_cso_grid sg on m.stream_cso_id = sg.stream_cso_id
         join grid_references gr on sg.grid_reference = gr.grid_reference
         JOIN ranked_events e ON m.stream_cso_id = e.stream_cso_id AND e.rnk = 1
where event = 'Start' 
order by m.stream_company, m.stream_id
            """.trimIndent(),
                bind = {
                    it.set(1, instant)
                },
                mapper = {
                    StreamCSOLiveOverflow(
                        id = it.get(StreamId, "stream_id"),
                        pcon24nm = it.get(ConstituencyName, "pcon24nm"),
                        company = it.get(StreamCompanyName, "stream_company").asCompanyName() ?: CompanyName("unknown"),
                        started = it.getTimestamp("event_time").toInstant(),
                        loc = Coordinates(
                            lat = it.getFloat("lat"),
                            lon = it.getFloat("lon")
                        )
                    )
                }
            )
        })
    }

    fun latestAvailable(): Instant {
        return connection.execute(NamedQueryBlock("stream-have-live-data") {
            query(
                sql = """
select f.stream_file_id, file_time, process_time
from stream_files f
join stream_files_processed fp on f.stream_file_id = fp.stream_file_id
order by f.file_time desc
limit 1                    
                """.trimIndent(),
                mapper = {
                    it.get("file_time")
                }
            ).first()
        })
    }

    fun haveLiveDataForCompanies(): Set<StreamCompanyName> {
        return connection.execute(NamedQueryBlock("stream-have-live-data") {
            query(
                sql = """
select distinct stream_company from stream_cso                    
                """.trimIndent(),
                mapper = {
                    StreamCompanyName.of(it.getString("stream_company"))
                }
            ).toSet()
        })
    }

    fun haveLiveDataForConstituencies(): Set<ConstituencyName> {
        return connection.execute(NamedQueryBlock("stream-have-live-data") {
            query(
                sql = """
select distinct pcon24nm
from grid_references
join stream_cso_grid on stream_cso_grid.grid_reference = grid_references.grid_reference
order by pcon24nm;                    
                """.trimIndent(),
                mapper = {
                    it.get(ConstituencyName, "pcon24nm")
                }
            ).toSet()
        })
    }

    data class StreamCsoSummary(
        val company: CompanyName,
        val id: String,
        val location: Coordinates,
        val duration: Duration,
        val days: Int,
    )

    fun byCsoForConstituency(
        constituency: ConstituencyName,
        startDate: LocalDate, endDate: LocalDate
    ): List<StreamCsoSummary> {
        return connection.execute(NamedQueryBlock("stream-live-events") {
            query(
                sql = """
select
    cso.stream_id,
    cso.stream_company,
    cso.lat, cso.lon,
    extract(epoch from sum(start)) as duration_seconds,
    count(*) filter (where start <> interval '0') as count,
        (
        select json_agg(
                   json_build_object(
                       'month', to_char(month_start, 'YYYY-MM'),
                       'count', cnt,
                       'duration_seconds', dur
                   )
                   order by month_start
               )
        from (
            select
                date_trunc('month', ss.date) as month_start,
                count(*) filter (where ss.start <> interval '0') as cnt,
                extract(epoch from sum(ss.start)) as dur
            from stream_summary ss
            where ss.stream_cso_id = cso.stream_cso_id and ss.date >= ? and ss.date <= ?
            group by date_trunc('month', ss.date)
        ) m
    ) as counts_by_month
from stream_summary
         join stream_cso as cso on cso.stream_cso_id = stream_summary.stream_cso_id
         join stream_cso_grid on cso.stream_cso_id = stream_cso_grid.stream_cso_id
         join grid_references on stream_cso_grid.grid_reference = grid_references.grid_reference
where grid_references.pcon24nm = ? and date >= ? and date <= ?
group by cso.stream_cso_id;
                """.trimIndent(),
                bind = {
                    it.set(1, startDate)
                    it.set(2, endDate)
                    it.set(3, constituency)
                    it.set(4, startDate)
                    it.set(5, endDate)
                },
                mapper = {
                    StreamCsoSummary(
                        id = it.getString("stream_id"),
                        company = it.get(StreamCompanyName, "stream_company").asCompanyName() ?: CompanyName("unknown"),
                        location = Coordinates(
                            lat = it.getFloat("lat"),
                            lon = it.getFloat("lon")
                        ),
                        duration = Duration.ofSeconds(it.getLong("duration_seconds")),
                        days = it.getInt("count")
                    )
                }
            )
        })
    }


    data class ConstituencyLiveTotal(
        val constituency: ConstituencyName,
        val duration: Duration,
        val csoCount: Int
    )

    fun totalForConstituency(
        constituency: ConstituencyName,
        startDate: LocalDate,
        endDate: LocalDate
    ): ConstituencyLiveTotal {
        return connection.execute(NamedQueryBlock("stream-live-events") {
            query(
                sql = """
select grid_references.pcon24nm, extract(epoch from sum(start)) as overflowing, count(distinct stream_cso.stream_cso_id) as cso_count
from stream_summary
         join stream_cso on stream_cso.stream_cso_id = stream_summary.stream_cso_id
         join stream_cso_grid on stream_cso.stream_cso_id = stream_cso_grid.stream_cso_id
         join grid_references on stream_cso_grid.grid_reference = grid_references.grid_reference
where grid_references.pcon24nm = ? and date >= ? and date <= ?
group by grid_references.pcon24nm
                """.trimIndent(),
                bind = {
                    it.set(1, constituency)
                    it.set(2, startDate)
                    it.set(3, endDate)
                },
                mapper = {
                    ConstituencyLiveTotal(
                        constituency = constituency,
                        duration = Duration.ofSeconds(it.getLong("overflowing")),
                        csoCount = it.getInt("cso_count")
                    )
                }
            )
        }).firstOrNull() ?: ConstituencyLiveTotal(
            constituency = constituency,
            duration = Duration.ZERO,
            csoCount = 0
        )

    }

    fun infrastructureSummary(company: StreamCompanyName): List<DatedOverflow> {
        return connection.execute(block("infrastructureSummary") {
            query(
                sql = """
select cso.stream_company, date,
       count(*) as edm_count,
       extract(epoch from sum(start)) as overflowingSeconds,
       count(case when start > interval '30m' then 1 end) as overflowing,
       count(case when offline > interval '30m' then 1 end) as offline
from stream_summary ss
    join stream_cso cso on ss.stream_cso_id = cso.stream_cso_id
where stream_company = ?
group by cso.stream_company, date
order by date
""",
                bind = {
                    it.setString(1, company.value)
                },
                mapper = {
                    DatedOverflow(
                        it.getDate("date").toLocalDate(),
                        it.getInt("edm_count"),
                        it.getInt("overflowing"),
                        it.getInt("overflowingSeconds"),
                        it.getInt("offline")
                    )
                }
            )
        })
    }

    data class ConstituencyEventMetrics(
        val constituencyName: ConstituencyName,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val count: Int
    ) : Event

    fun eventSummaryForConstituency(
        constituencyName: ConstituencyName,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Thing> {
        var counter = 0
        return connection.execute(NamedQueryBlock("stream-live-events") {
            query(
                sql = """
select stream_id,
       date,
       extract(epoch from stop) as stop,
       extract(epoch from offline) as offline,
       extract(epoch from start) as start,
       extract(epoch from unknown) as unknown,
       extract(epoch from potential_start) as potential_start
from stream_summary
join stream_cso on stream_cso.stream_cso_id = stream_summary.stream_cso_id
join stream_cso_grid on stream_cso.stream_cso_id = stream_cso_grid.stream_cso_id
join grid_references on stream_cso_grid.grid_reference = grid_references.grid_reference
where grid_references.pcon24nm = ? and date >= ? and date <= ?
order by stream_id, date;
                """.trimIndent(),
                bind = {
                    it.set(1, constituencyName)
                    it.set(2, startDate)
                    it.set(3, endDate)
                },
                mapper = {
                    counter++
                    Thing(
                        p = it.getString("stream_id"),
                        cid = it.getString("stream_id"),
                        d = it.getDate("date").toLocalDate(),
                        a = codeFrom(
                            Bucket(
                                online = it.getInt("stop"),
                                offline = it.getInt("offline"),
                                overflowing = it.getInt("start"),
                                unknown = it.getInt("unknown"),
                                potentially_overflowing = it.getInt("potential_start"),
                            )
                        )
                    )
                }
            )
        }).also {
            events(ConstituencyEventMetrics(constituencyName, startDate, endDate, counter))
        }
    }
}