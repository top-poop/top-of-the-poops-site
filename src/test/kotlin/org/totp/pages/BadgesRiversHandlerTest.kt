package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars


class BadgesRiversHandlerTest {

    val service = routes(
        "/" bind Method.GET to BadgesRiversHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            riverRankings = { listOf(aRiver(1)) },
        )
    )

    @Test
    fun `renders the badges  page`() {
        val html = Html(service(Request(Method.GET, "/")))
    }
}