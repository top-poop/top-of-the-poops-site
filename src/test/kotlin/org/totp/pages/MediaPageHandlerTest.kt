package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Test
import org.totp.model.TotpHandlebars
import org.totp.model.data.MediaAppearance
import java.time.LocalDate


class MediaPageHandlerTest {

    val service = routes(
        "/" bind Method.GET to MediaPageHandler(
            renderer = TotpHandlebars.templates().HotReload("src/main/resources/templates/page/org/totp"),
            appearances = {
                listOf(
                    MediaAppearance(
                        title = "Here",
                        publication = "Mag",
                        date = LocalDate.of(1996, 12, 25),
                        uri = Uri.of("http:/example.com"),
                        imageUri = Uri.of("http://example.com/image.jpg")
                    )
                )
            }
        )
    )

    @Test
    fun `renders the page`() {
        Html(service(Request(Method.GET, "/")))
    }
}