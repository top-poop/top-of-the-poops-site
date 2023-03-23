package org.totp.http4k

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.core.query
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class UriConstructionTest {
    @Test
    fun `constructing from regular request`() {
        val uri = Uri.of("/bob").query("a", "b").query("c", "d")
        expectThat(
            pageUriFrom(
                Request(Method.GET, uri)
                    .header("Host", "localhost:8080")
            )
        ).isEqualTo(Uri.of("http://localhost:8080/bob?a=b&c=d"))
    }

    @Test
    fun `constructing from proxied request`() {
        val uri = Uri.of("/bob").query("a", "b").query("c", "d")
        expectThat(
            pageUriFrom(
                Request(Method.GET, uri)
                    .header("X-Forwarded-Host", "service:8080")
                    .header("X-Forwarded-Proto", "https")
                    .header("Host", "proxy")
            )
        ).isEqualTo(Uri.of("https://service:8080/bob?a=b&c=d"))
    }
}