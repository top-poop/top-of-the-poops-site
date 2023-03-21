package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.strikt.bodyString
import org.http4k.strikt.header
import org.http4k.strikt.status
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo

class ConstituencyPageHandlerTest {

    val service = routes(
        "/{constituency}" bind Method.GET to ConstituencyPageHandler()
    )

    @Test
    fun `renders a constituency`() {
        expectThat(service(Request(Method.GET, "/aldershot"))).status.isEqualTo(Status.OK)
    }

    @Test
    fun `redirects non-kebab to kebab`() {
        expectThat(service(Request(Method.GET, "/Aldershot"))) {
            status.isEqualTo(Status.TEMPORARY_REDIRECT)
            header("location").isEqualTo("/aldershot")
        }

        expectThat(service(Request(Method.GET, "/Islington South and Finsbury"))) {
            status.isEqualTo(Status.TEMPORARY_REDIRECT)
            header("location").isEqualTo("/islington-south-and-finsbury")
        }
    }
}