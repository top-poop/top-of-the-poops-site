package org.totp.http4k

import org.http4k.core.Uri
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class StandardFiltersKtTest {


    @Test
    fun `removes queries`() {
        val uri = Uri.of("http://example.com?query&blah=blah")
        expectThat(uri.removeQuery()).isEqualTo(Uri.of("http://example.com"))
    }
}