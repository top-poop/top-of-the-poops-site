package org.totp.pages

import org.http4k.core.*
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel
import org.http4k.template.viewModel

object ConstituencyRendering {
    operator fun invoke(): HttpHandler {
        val renderer = HandlebarsTemplates().HotReload(
            "src/main/resources/templates/page",
            "src/main/resources/templates/page/org/totp/components"
        )
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { _: Request ->
            Response(Status.OK).with(viewLens of ConstituencyPage("bob"))
        }
    }
}



data class Location(val lat:Float, val lon: Float)

data class PoliticalParty(
    val name: String,
    val twitter: String
)

data class MP(
    val name: String,
    val party: PoliticalParty,
    val twitter: String,
)

data class Constituency(
    val name: String,
    val location: Location,
    val mp: MP,
)

data class Constituencies(val constituencies: List<Constituency>)

data class ConstituencyPage(val name: String) : ViewModel