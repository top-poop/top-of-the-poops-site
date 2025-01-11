package org.totp.db

import org.totp.model.data.CompanyName
import org.totp.model.data.Coordinates
import java.time.Instant

class StreamData(private val connection: WithConnection) {

    data class StreamCSOLiveOverflow(
        val company: CompanyName,
        val started: Instant,
        val id: String,
        val loc: Coordinates,
    )

    fun overflowingRightNow(): List<StreamCSOLiveOverflow> {
        return connection.execute(NamedQueryBlock("overflowing-right-now") {
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