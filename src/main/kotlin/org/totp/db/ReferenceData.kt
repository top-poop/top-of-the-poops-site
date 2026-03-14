package org.totp.db

import org.http4k.core.Uri
import org.totp.db.NamedQueryBlock.Companion.block
import org.totp.model.data.ConstituencyName
import org.totp.model.data.Coordinates
import org.totp.model.data.PlaceName
import org.totp.model.data.SeneddConstituencyName
import org.totp.pages.MP


class ReferenceData(private val connection: WithConnection) {

    fun westminsterConstituenciesFor(constituency: SeneddConstituencyName): List<ConstituencyName> {
        return connection.execute(block("westminster-constituencies-for") {
            query(
                sql = """
select pcon24nm 
from senedd_cons sc
    join senedd_final_2026 s on s.ogc_fid = sc.ogc_fid
WHERE s.english_na = ?
            """.trimIndent(),
                bind = {
                    it.setString(1, constituency.value)
                },
                mapper = { row ->
                    row.get(ConstituencyName, "pcon24nm")
                },
            )
        })
    }

    fun constituencyFor(place: PlaceName): ConstituencyName {
        return connection.execute(block("find-constituency-for-place") {
            query(
                sql = """
SELECT
    p.pcon24nm
FROM pcon_july_2024_uk_bfc p
         JOIN os_open_built_up_areas a
              ON ST_Contains(p.wkb_geometry, ST_PointOnSurface(a.geometry))
WHERE a.name1_text = ?
            """.trimIndent(),
                bind = {
                    it.setString(1, place.value)
                },
                mapper = { row ->
                    row.get(ConstituencyName, "pcon24nm")
                },
            )
        }).first()
    }

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