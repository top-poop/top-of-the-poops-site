package org.totp.extensions

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class StringExtensionsKtTest {

    @Test
    fun `kebab stuff`() {
        expectThat("bob".kebabCase()).isEqualTo("bob")
        expectThat("bob smith".kebabCase()).isEqualTo("bob-smith")
        expectThat("Bob Smith".kebabCase()).isEqualTo("bob-smith")
        expectThat("Bob,Smith".kebabCase()).isEqualTo("bob-smith")
    }
}