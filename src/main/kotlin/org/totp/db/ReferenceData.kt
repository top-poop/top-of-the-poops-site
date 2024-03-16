package org.totp.db

import org.http4k.core.Uri
import org.totp.db.NamedQueryBlock.Companion.block
import org.totp.model.data.ConstituencyName
import org.totp.pages.MP

class ReferenceData(private val connection: WithConnection) {

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