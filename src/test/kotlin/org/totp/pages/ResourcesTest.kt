package org.totp.pages

import org.http4k.core.Method
import org.http4k.core.Uri
import org.http4k.template.ViewModel
import org.junit.jupiter.api.Test
import org.totp.Resources
import org.totp.model.TotpHandlebars
import strikt.api.expectThat
import strikt.assertions.isNotNull

class ResourcesTest {

    @Test
    fun `loading templates in prod mode`() {

        val renderer = Resources.templates(TotpHandlebars.templates(), devMode = false)

        renderer(object : ViewModel { override fun template(): String = "chunks/copyright" })
    }

    @Test
    fun `loading templates in dev mode`() {

        val renderer = Resources.templates(TotpHandlebars.templates(), devMode = true)

        renderer(object : ViewModel { override fun template(): String = "chunks/copyright" })
    }

    @Test
    fun `loading assets in dev mode`() {
        val loader = Resources.assets(devMode = true)
        expectThat(loader.load("poop.png")).isNotNull()
    }

    @Test
    fun `loading assets in prod mode`() {
        val loader = Resources.assets(devMode = false)
        expectThat(loader.load("poop.png")).isNotNull()
    }
}