package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.strikt.status
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class ConstituencyRenderingTest {

    @org.junit.jupiter.api.Test
    fun `renders a person`() {
        val service = ConstituencyRendering()
        expectThat(service(Request(Method.GET, "/"))).status.isEqualTo(Status.OK)
    }
}