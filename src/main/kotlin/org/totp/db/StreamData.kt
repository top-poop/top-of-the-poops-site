package org.totp.db

import dev.forkhandles.values.StringValue
import dev.forkhandles.values.StringValueFactory
import org.http4k.events.Event
import org.http4k.events.Events
import org.totp.db.NamedQueryBlock.Companion.block
import org.totp.model.data.*
import java.sql.ResultSet
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

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
        val site_name: SiteName,
        val receiving_water: WaterwayName,
    )

    data class StreamCSOCount(val start: Int, val stop: Int) {
        val total = start + stop
    }

    data class StreamCompanyStatus(val company: StreamCompanyName, val count: StreamCSOCount)

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
                company = StreamCompanyName.of(it.key),
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

    fun csosWithinBBox(ne: Coordinates, sw: Coordinates): List<StreamCSOLiveOverflow> {
        return connection.execute(NamedQueryBlock("stream-csos-within-bbox") {
            query(
                sql = """                
SELECT gr.pcon24nm, m.stream_company, m.stream_id, sl.site_name_wasc, sl.site_name_consent, sl.receiving_water, m.lat, m.lon
FROM stream_cso m
         join stream_cso_grid sg on m.stream_cso_id = sg.stream_cso_id
         join grid_references gr on sg.grid_reference = gr.grid_reference
         left join stream_lookup sl on (m.stream_id = sl.stream_id or m.stream_id = sl.stream_id_old)
where  m.point && ST_MakeEnvelope(?, ?, ?, ?, 4326);
 """,
                bind = {
                    it.setDouble(1, sw.lon)
                    it.setDouble(2, sw.lat)
                    it.setDouble(3, ne.lon)
                    it.setDouble(4, ne.lat)
                },
                mapper = {
                    StreamCSOLiveOverflow(
                        it.get(StreamId, "stream_id"),
                        company = it.get(StreamCompanyName, "stream_company").asCompanyName() ?: CompanyName("unknown"),
                        pcon24nm = it.get(ConstituencyName, "pcon24nm"),
                        started = Instant.MIN,
                        loc = Coordinates(lat = it.getDouble("lat"), lon = it.getDouble("lon")),
                        site_name = it.getNullable(SiteName, "site_name_wasc") ?: it.getNullable(
                            SiteName,
                            "site_name_consent"
                        ) ?: SiteName.of("Unknown"),
                        receiving_water = it.getNullable(WaterwayName, "receiving_water") ?: WaterwayName.of("Unknown")
                    )
                }
            )
        })
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
SELECT gr.pcon24nm, m.stream_company, m.stream_id, sl.site_name_wasc, sl.site_name_consent, sl.receiving_water, m.lat, m.lon, e.event, e.event_time, e.update_time
FROM stream_cso m
         join stream_cso_grid sg on m.stream_cso_id = sg.stream_cso_id
         join grid_references gr on sg.grid_reference = gr.grid_reference
         JOIN ranked_events e ON m.stream_cso_id = e.stream_cso_id AND e.rnk = 1
         left join stream_lookup sl on (m.stream_id = sl.stream_id or m.stream_id = sl.stream_id_old) 
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
                            lat = it.getDouble("lat"),
                            lon = it.getDouble("lon")
                        ),
                        site_name = it.getNullable(SiteName, "site_name_wasc") ?: it.getNullable(
                            SiteName,
                            "site_name_consent"
                        ) ?: SiteName.of("Unknown"),
                        receiving_water = it.getNullable(WaterwayName, "receiving_water") ?: WaterwayName.of("Unknown")
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
        val id: StreamId,
        val site_name: SiteName,
        val receiving_water: WaterwayName,
        val location: Coordinates,
        val start: Duration,
        val offline: Duration,
        val potential: Duration,
        val days: Int,
        val pcon24nm: ConstituencyName,
    )

    fun byCsoForConstituency(
        constituency: ConstituencyName,
        startDate: LocalDate, endDate: LocalDate
    ): List<StreamCsoSummary> {
        return connection.execute(NamedQueryBlock("by-cso-for-constituency") {
            query(
                sql = """
select
    cso.stream_id,
    cso.stream_company,
    cso.lat, cso.lon,
    sl.site_name_consent,
    sl.site_name_wasc,
    sl.receiving_water,
    grid_references.pcon24nm,
    extract(epoch from sum(start)) as start,
    extract(epoch from sum(offline)) as offline,
    extract(epoch from sum(potential_start)) as potential,
    count(*) filter (where start <> interval '0') as count
        
from stream_summary
         join stream_cso as cso on cso.stream_cso_id = stream_summary.stream_cso_id
         join stream_cso_grid on cso.stream_cso_id = stream_cso_grid.stream_cso_id
         join grid_references on stream_cso_grid.grid_reference = grid_references.grid_reference
         left join stream_lookup sl on (cso.stream_id = sl.stream_id or cso.stream_id = sl.stream_id_old) 
where grid_references.pcon24nm = ? and date >= ? and date <= ?
group by grid_references.pcon24nm, cso.stream_cso_id, sl.site_name_consent, sl.site_name_wasc, sl.receiving_water;
                """.trimIndent(),
                bind = {
                    it.set(1, constituency)
                    it.set(2, startDate)
                    it.set(3, endDate)
                },
                mapper = {
                    streamCsoSummaryFrom(it)
                }
            )
        })
    }

    data class DailySummary(val date: LocalDate, val start: Duration, val offline: Duration, val potential: Duration)

    fun dailyByStreamId(streamId: StreamId, start: LocalDate, end: LocalDate): List<DailySummary> {
        return connection.execute(NamedQueryBlock("daily-by-constituency") {
            query(
                sql = """
select ss.date, 
    extract(epoch from sum(start)) as start, 
    extract(epoch from sum(offline)) as offline, 
    extract(epoch from sum(potential_start)) as potential, 
    count(distinct stream_cso.stream_cso_id) as cso_count
from stream_summary ss
         join stream_cso on stream_cso.stream_cso_id = ss.stream_cso_id and date >= ? and date <= ?
where stream_cso.stream_id = ? 
group by ss.date
""",
                bind = {
                    it.set(1, start)
                    it.set(2, end)
                    it.set(3, streamId.value)
                },
                mapper = {
                    DailySummary(
                        it.getDate("date").toLocalDate(),
                        start = Duration.ofSeconds(it.getLong("start")),
                        offline = Duration.ofSeconds(it.getLong("offline")),
                        potential = Duration.ofSeconds(it.getLong("potential")),
                    )
                }
            )
        })
    }

    fun dailyByConstituency(constituency: ConstituencyName, start: LocalDate, end: LocalDate): List<DailySummary> {
        return connection.execute(NamedQueryBlock("daily-by-constituency") {
            query(
                sql = """
select ss.date, 
    extract(epoch from sum(start)) as start, 
    extract(epoch from sum(offline)) as offline, 
    extract(epoch from sum(potential_start)) as potential, 
    count(distinct stream_cso.stream_cso_id) as cso_count
from stream_summary ss
         join stream_cso on stream_cso.stream_cso_id = ss.stream_cso_id
         join stream_cso_grid on stream_cso.stream_cso_id = stream_cso_grid.stream_cso_id
         join grid_references on stream_cso_grid.grid_reference = grid_references.grid_reference
where grid_references.pcon24nm = ? and date >= ? and date <= ?
group by grid_references.pcon24nm, ss.date
""",
                bind = {
                    it.set(1, constituency)
                    it.set(2, start)
                    it.set(3, end)
                },
                mapper = {
                    DailySummary(
                        it.getDate("date").toLocalDate(),
                        start = Duration.ofSeconds(it.getLong("start")),
                        offline = Duration.ofSeconds(it.getLong("offline")),
                        potential = Duration.ofSeconds(it.getLong("potential")),
                    )
                }
            )
        })
    }

    data class ConstituencyLiveTotal(
        val constituency: ConstituencyName,
        val start: Duration,
        val csoCount: Int,
        val offline: Duration,
        val potential: Duration,
    )

    fun totalForConstituency(
        constituency: ConstituencyName,
        startDate: LocalDate,
        endDate: LocalDate
    ): ConstituencyLiveTotal {
        return connection.execute(NamedQueryBlock("total-for-constituency") {
            query(
                sql = """
select 
    grid_references.pcon24nm, 
    extract(epoch from sum(start)) as start, 
    extract(epoch from sum(offline)) as offline, 
    extract(epoch from sum(potential_start)) as potential, 
    count(distinct stream_cso.stream_cso_id) as cso_count
from stream_cso 
         join stream_summary on stream_cso.stream_cso_id = stream_summary.stream_cso_id and date >= ? and date <= ?
         join stream_cso_grid on stream_cso.stream_cso_id = stream_cso_grid.stream_cso_id
         join grid_references on stream_cso_grid.grid_reference = grid_references.grid_reference
where grid_references.pcon24nm = ? 
group by grid_references.pcon24nm
                """.trimIndent(),
                bind = {
                    it.set(1, startDate)
                    it.set(2, endDate)
                    it.set(3, constituency)
                },
                mapper = {
                    ConstituencyLiveTotal(
                        constituency = constituency,
                        start = Duration.ofSeconds(it.getLong("start")),
                        offline = Duration.ofSeconds(it.getLong("offline")),
                        potential = Duration.ofSeconds(it.getLong("potential")),
                        csoCount = it.getInt("cso_count")
                    )
                }
            )
        }).firstOrNull() ?: ConstituencyLiveTotal(
            constituency = constituency,
            start = Duration.ZERO,
            offline = Duration.ZERO,
            potential = Duration.ZERO,
            csoCount = 0,
        )
    }

    fun monthlyOverflowingByCompany(company: StreamCompanyName): List<DatedOverflow> {
        return connection.execute(block("stream-overflowing-monthly-by-company") {
            query(
                sql = """
select
    cso.stream_company,
    date_trunc('month', date)::date as month,
    count(distinct cso.stream_cso_id) as edm_count,
    extract(epoch from sum(start)) as overflowingSeconds,
       count(distinct case
        when start > interval '30 minutes'
        then cso.stream_cso_id
    end) as overflowing,

    count(distinct case
        when offline > interval '30 minutes'
        then cso.stream_cso_id
    end) as offline
    
from stream_summary ss
join stream_cso cso
  on ss.stream_cso_id = cso.stream_cso_id
where stream_company = ?
group by
    cso.stream_company,
    date_trunc('month', date)
order by month;
""",
                bind = {
                    it.setString(1, company.value)
                },
                mapper = {
                    DatedOverflow(
                        it.getDate("month").toLocalDate(),
                        it.getInt("edm_count"),
                        it.getInt("overflowing"),
                        it.getLong("overflowingSeconds"),
                        it.getInt("offline")
                    )
                }
            )
        }).filter { it.date.isAfter(LocalDate.parse("2024-12-31")) }
    }

    fun dailyOverflowingByCompany(company: StreamCompanyName): List<DatedOverflow> {
        return connection.execute(block("stream-overflowing-daily-by-company") {
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
                        it.getLong("overflowingSeconds"),
                        it.getInt("offline")
                    )
                }
            )
        })
    }

    data class CSOEvent(
        val id: StreamId,
        val fileTime: Instant,
        val statusStart: Instant?,
        val status: String,
        val latestEventStart: Instant?,
        val latestEventEnd: Instant?,
        val lastUpdated: Instant?
    )

    fun eventsForCso(id: StreamId, start: LocalDate, end: LocalDate): List<CSOEvent> {
        return connection.execute(block("cso-events") {
            query(
                sql = """
select file_time, status, statusstart, latesteventstart, latesteventend, lastupdated
    from stream_files sf
    join stream_file_events sfc on sf.stream_file_id = sfc.stream_file_id
    where sfc.id = ?
        and sf.file_time >= ? and sf.file_time < ?
        order by file_time desc
        ;
                """.trimMargin(),
                bind = {
                    it.set(1, id.value)
                    it.set(2, start)
                    it.set(3, end)
                },
                mapper = {
                    CSOEvent(
                        id,
                        fileTime = it.get("file_time"),
                        statusStart = it.getNullable("statusstart"),
                        status = it.getString("status"),
                        lastUpdated = it.getNullable("lastupdated"),
                        latestEventEnd = it.getNullable("latesteventend"),
                        latestEventStart = it.getNullable("latesteventstart")
                    )
                }
            )
        })
    }

    data class DatedBucket(val date: LocalDate, val data: Bucket, val partial: Boolean, val future: Boolean)

    data class CompanyBucket(val company: StreamCompanyName, val bucket: Bucket)

    fun totalsByCompany(start: LocalDate, end: LocalDate): List<CompanyBucket> {
        return connection.execute(block("cso-buckets") {
            query(
                sql = """
select stream_company,
       coalesce(extract(epoch from sum(stop)),0) as stop,
       coalesce(extract(epoch from sum(start)),0) as start,
       coalesce(extract(epoch from sum(offline)),0) as offline,
       coalesce(extract(epoch from sum(unknown)),0) as unknown,
       coalesce(extract(epoch from sum(potential_start)),0) as potential_start,
       count(*) filter (where start <> interval '0') as count
    from stream_cso cso
    join stream_summary ss on cso.stream_cso_id = ss.stream_cso_id and ss.date >= ? and ss.date < ?
group by stream_company
                    """,
                bind = {
                    it.set(1, start)
                    it.set(2, end)
                },
                mapper = {
                    CompanyBucket(
                        it.get(StreamCompanyName, "stream_company"),
                        bucketFrom(it)
                    )
                }
            )
        });
    }


    fun csoMonthlyBuckets(id: StreamId, current: LocalDate, start: LocalDate, end: LocalDate): List<DatedBucket> {
        return connection.execute(block("cso-buckets") {
            query(
                sql = """
with months as (
    select generate_series(
        date_trunc('month', ?::date),
        date_trunc('month', ?::date) - interval '1 month',
        interval '1 month'
    )::date as month
),
aggregated as (
    select
        date_trunc('month', s.date)::date as month,
        sum(s.stop) as stop,
        sum(s.offline) as offline,
        sum(s.start) as start,
        sum(s.unknown) as unknown,
        sum(s.potential_start) as potential_start
    from stream_summary s
    join stream_cso c
      on c.stream_cso_id = s.stream_cso_id
    where c.stream_id = ?
      and s.date >= ?
      and s.date < ?
    group by 1
)
select
    m.month,
    coalesce(extract(epoch from a.stop), 0) as stop,
    coalesce(extract(epoch from a.offline), 0) as offline,
    coalesce(extract(epoch from a.start), 0) as start,
    coalesce(extract(epoch from a.unknown), 0) as unknown,
    coalesce(extract(epoch from a.potential_start), 0) as potential_start
from months m
left join aggregated a on a.month = m.month
order by m.month;
                    """,
                bind = {
                    it.set(1, start)
                    it.set(2, end)
                    it.set(3, id.value)
                    it.set(4, start)
                    it.set(5, end)
                },
                mapper = {
                    val date = it.getDate("month").toLocalDate()
                    DatedBucket(
                        date = date,
                        partial = YearMonth.from(date).equals(YearMonth.from(current)),
                        future = date.isAfter(current),
                        data = bucketFrom(it)
                    )
                }
            )
        })
    }

    fun eventSummaryForCso(id: StreamId, current: LocalDate, start: LocalDate, end: LocalDate): List<DatedBucket> {
        return connection.execute(block("cso-buckets") {
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
where stream_id = ?
and date >= ? and date < ?
                """.trimIndent(),
                bind = {
                    it.setString(1, id.value)
                    it.set(2, start)
                    it.set(3, end)
                },
                mapper = {
                    val date = it.getDate("date").toLocalDate()
                    DatedBucket(
                        date,
                        partial = YearMonth.from(date).equals(YearMonth.from(current)),
                        future = date.isAfter(current),
                        data = bucketFrom(it)
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
        return connection.execute(NamedQueryBlock("event-summary-by-constituency") {
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
                            bucketFrom(it)
                        )
                    )
                }
            )
        }).also {
            events(ConstituencyEventMetrics(constituencyName, startDate, endDate, counter))
        }
    }


    fun cso(id: StreamId, start: LocalDate, end: LocalDate): StreamCsoSummary? {
        return connection.execute(NamedQueryBlock("by-cso") {
            querySingle(
                sql = """
select
    cso.stream_id,
    cso.stream_company,
    cso.lat, cso.lon,
    sl.site_name_consent,
    sl.site_name_wasc,
    sl.receiving_water,
    pcon24nm,
    coalesce(extract(epoch from sum(start)),0) as start,
    coalesce(extract(epoch from sum(offline)),0) as offline,
    coalesce(extract(epoch from sum(potential_start)),0) as potential,
    count(*) filter (where start <> interval '0') as count
from stream_cso cso
         left join stream_summary on cso.stream_cso_id = stream_summary.stream_cso_id and date >= ? and date <= ?
         join stream_cso_grid on cso.stream_cso_id = stream_cso_grid.stream_cso_id
         join grid_references on stream_cso_grid.grid_reference = grid_references.grid_reference
         left join stream_lookup sl on (cso.stream_id = sl.stream_id or cso.stream_id = sl.stream_id_old)
where cso.stream_id = ? 
group by cso.stream_id, cso.stream_company, cso.lat, cso.lon, pcon24nm, sl.site_name_consent, sl.site_name_wasc, sl.receiving_water;
                """.trimIndent(),
                bind = {
                    it.set(1, start)
                    it.set(2, end)
                    it.set(3, id.value)
                },
                mapper = {
                    streamCsoSummaryFrom(it)
                }
            )
        })
    }

    private fun streamCsoSummaryFrom(rs: ResultSet): StreamCsoSummary = StreamCsoSummary(
        id = rs.get(StreamId, "stream_id"),
        company = rs.get(StreamCompanyName, "stream_company").asCompanyName() ?: CompanyName("unknown"),
        location = Coordinates(
            lat = rs.getDouble("lat"),
            lon = rs.getDouble("lon")
        ),
        pcon24nm = rs.get(ConstituencyName, "pcon24nm"),
        start = Duration.ofSeconds(rs.getLong("start")),
        offline = Duration.ofSeconds(rs.getLong("offline")),
        potential = Duration.ofSeconds(rs.getLong("potential")),
        days = rs.getInt("count"),
        site_name = rs.getNullable(SiteName, "site_name_wasc") ?: rs.getNullable(
            SiteName,
            "site_name_consent"
        ) ?: SiteName.of("Unknown"),
        receiving_water = rs.getNullable(WaterwayName, "receiving_water") ?: WaterwayName.of("Unknown")
    )
}

fun bucketFrom(rs: ResultSet): Bucket = Bucket(
    online = Duration.ofSeconds(rs.getLong("stop")),
    offline = Duration.ofSeconds(rs.getLong("offline")),
    start = Duration.ofSeconds(rs.getLong("start")),
    unknown = Duration.ofSeconds(rs.getLong("unknown")),
    potential = Duration.ofSeconds(rs.getLong("potential_start")),
)
