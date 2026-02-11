package org.totp.redirect

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.db.HikariWithConnection
import org.totp.db.ReferenceData
import org.totp.db.datasource
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class ConstituencyAtRedirectHandlerTest {

    val connection = HikariWithConnection(lazy { datasource() })

    val rd = ReferenceData(connection)

    val handler = routes("/c/at/{lat}/{lon}" bind ConstituencyAtRedirectHandler(rd))

    @Test
    fun `redirect appropriately - edm`() {
        expectThat(handler(Request(Method.GET, Uri.of("/c/at/50.826/-2.308")))) {
            get { status }.isEqualTo(Status.TEMPORARY_REDIRECT)
            get { header("Location") }.isEqualTo("/constituency/west-dorset")
        }
    }

    @Test
    fun `redirect appropriately - live`() {
        expectThat(handler(Request(Method.GET, Uri.of("/c/at/1/1?live")))) {
            get { status }.isEqualTo(Status.TEMPORARY_REDIRECT)
            get { header("Location") }.isEqualTo("/constituency/st-ives/live")
        }
    }
}