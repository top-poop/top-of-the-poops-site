package org.totp.db

import org.http4k.core.Uri
import org.totp.db.NamedQueryBlock.Companion.block
import org.totp.model.data.ConstituencyName
import org.totp.model.data.Coordinates
import org.totp.pages.MP


class ReferenceData(private val connection: WithConnection) {

    fun constituencyAt(location: Coordinates): ConstituencyName? {
        return connection.execute(block("find-constituency-within") {
            query(
                sql = """
WITH input AS (SELECT ST_SetSRID(ST_MakePoint(? /* lon */, ? /* lat */), 4326) AS pt)
SELECT p.*
FROM pcon_july_2024_uk_bfc p,
     input i
WHERE ST_Intersects(p.wkb_geometry, i.pt)
LIMIT 1;                
            """.trimIndent(),
                bind = {
                    it.setDouble(1, location.lon)
                    it.setDouble(2, location.lat)
                },
                mapper = { row ->
                    row.get(ConstituencyName, "pcon24nm")
                },
                )
        }).firstOrNull()
    }

    fun constituencyNear(location: Coordinates): ConstituencyName? {
        return connection.execute(block("find-constituency-nearest") {
            query(
                sql = """
WITH input AS (SELECT ST_SetSRID(ST_MakePoint(? /* lon */, ? /* lat */), 4326) AS pt)
SELECT p.*, 1 AS priority
FROM pcon_july_2024_uk_bfc p, input i
ORDER BY p.wkb_geometry <-> i.pt
LIMIT 1;
            """.trimIndent(),
                bind = {
                    it.setDouble(1, location.lon)
                    it.setDouble(2, location.lat)
                },
                mapper = { row ->
                    row.get(ConstituencyName, "pcon24nm")
                },
                )
        }).firstOrNull()
    }

    fun mps(): List<ConstituencyContact> {

        return connection.execute(block("list-mps") {
            query(
                sql = """
                    select mps.constituency,
                           mps.first_name, 
                           mps.last_name,
                           mps.party                                  as mp_party,
                           mps.uri                                    as mp_uri,
                           mps_twitter.screen_name                    as twitter_handle
                    from mps
                             left join mps_twitter on mps.constituency = mps_twitter.constituency
                """.trimIndent(),
                mapper = {
                    ConstituencyContact(
                        it.get(ConstituencyName, "constituency"),
                        MP(
                            it.getString("first_name") + " " + it.getString("last_name"),
                            it.getString("mp_party"),
                            it.getString("twitter_handle"),
                            Uri.of(it.getString("mp_uri")),
                        )
                    )
                }
            )
        })
    }
}

data class ConstituencyContact(
    val constituency: ConstituencyName,
    val mp: MP,
)