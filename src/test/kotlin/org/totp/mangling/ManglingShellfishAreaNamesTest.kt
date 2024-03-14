package org.totp.mangling

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.totp.db.*
import org.totp.extensions.kebabCase
import org.totp.model.data.ShellfishAreaName
import org.totp.model.data.ShellfisheryName


class ManglingShellfishAreaNamesTest {


    val connection = HikariWithConnection(lazy { datasource() })

    @Test
    @Disabled("not actually a test")
    fun something() {

        val u: Unit = connection.execute(NamedQueryBlock("something") {

            val names = query(
                sql = "select distinct shellfishery from edm_consent_view where shellfishery is not null",
                mapper = {
                    it.get("shellfishery", ::ShellfisheryName)
                }
            )

            val actual = query(
                sql = "select distinct name from shellfish_view",
                mapper = {
                    it.get("name", ::ShellfishAreaName)
                }
            ).associateBy { it.value.kebabCase() }
                .toSortedMap()

            val s = names.flatMap {
                it.value.split("/", ",", " and ").map { it.trim() }
            }.toSet()

            println("Initial = ${s.size}")

            val results = s
                .filterNot { it.kebabCase() in actual }
                .sorted()

            println(results.joinToString(separator = "\n"))

            println("Missing = ${results.size}")

            Unit
        })


    }


}