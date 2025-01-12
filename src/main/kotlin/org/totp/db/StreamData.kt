package org.totp.db

import org.totp.model.data.CompanyName
import org.totp.model.data.Coordinates
import java.time.Instant

class StreamData(private val connection: WithConnection) {

    enum class StreamEvent(val dbName: String) {
        Start("Start"),
        Stop("Stop"),
        Unknown("Unknown")
    }

    data class StreamCSOLiveOverflow(
        val company: CompanyName,
        val started: Instant,
        val id: String,
        val loc: Coordinates,
    )

    data class StreamCSOCount(val start: Int, val stop: Int) {
        val total = start + stop
    }
    data class StreamCompanyStatus(val company: CompanyName, val count: StreamCSOCount)

    data class StreamOverflowSummary(val count: StreamCSOCount, val companies: List<StreamCompanyStatus>)

    fun summary(): StreamOverflowSummary {
        val summary = mutableMapOf<String, MutableMap<String, Int>>()
        connection.execute(NamedQueryBlock("stream-overflowing-right-now") {
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


    fun overflowingRightNow(): List<StreamCSOLiveOverflow> {
        return connection.execute(NamedQueryBlock("stream-overflowing-right-now") {
            query(
                sql = """                
WITH ranked_events AS (
    SELECT
        e.*,
        ROW_NUMBER() OVER (PARTITION BY e.stream_cso_id ORDER BY e.event_time DESC) AS rnk
    FROM
        stream_cso_event as e
)
SELECT m.stream_company, m.stream_id, m.lat, m.lon, e.event, e.event_time, e.update_time
FROM stream_cso m
         JOIN ranked_events e ON m.stream_cso_id = e.stream_cso_id AND e.rnk = 1
where event = 'Start'
order by m.stream_company, m.stream_id
            """.trimIndent(),
                mapper = {
                    StreamCSOLiveOverflow(
                        id = it.getString("stream_id"),
                        company = it.get(CompanyName, "stream_company"),
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

}