package org.totp.pages

import org.http4k.core.*
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel
import org.http4k.template.viewModel

object ConstituencyRendering {
    operator fun invoke(): HttpHandler {
        val renderer = HandlebarsTemplates().HotReload("src/main/resources/templates/page")
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { _: Request ->
            Response(Status.OK).with(viewLens of Constituency("bob"))
        }
    }
}

data class Constituency(val name: String) : ViewModel