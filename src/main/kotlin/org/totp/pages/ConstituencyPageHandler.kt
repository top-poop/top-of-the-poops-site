package org.totp.pages

import com.github.jknack.handlebars.Options
import com.github.jknack.handlebars.helper.StringHelpers
import dev.forkhandles.values.StringValue
import dev.forkhandles.values.StringValueFactory
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.lens.Header.LOCATION
import org.http4k.lens.Path
import org.http4k.lens.value
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel
import org.http4k.template.viewModel
import org.http4k.urlEncoded
import org.totp.extensions.kebabCase
import org.totp.text.csv.readCSV

val rawConstituencyNames = readCSV(
    resource = "/data/constituencies.csv",
    mapper = { ConstituencyName(it[0]) }
).toSet()

val kebabCaseConstituencyNames = rawConstituencyNames.associateBy {
    ConstituencyName(it.value.kebabCase())
}

object ConstituencyPageHandler {
    operator fun invoke(): HttpHandler {
        val renderer = HandlebarsTemplates {
            it.registerHelperMissing { _: Any, options: Options ->
                throw IllegalArgumentException(
                    "Missing value for: " + options.helperName
                )
            }
            StringHelpers.register(it)
            it.registerHelper("urlencode") { context: Any, options: Options -> context.toString().urlEncoded() }
        }.HotReload(
            "src/main/resources/templates/page/org/totp"
        )
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        val constituency = Path.value(ConstituencyName).of("constituency", "The constituency")

        return { request: Request ->
            val constituencyName = constituency(request)

            if (rawConstituencyNames.contains(constituencyName)) {
                val redirect = request.uri.path.replace(constituencyName.value, constituencyName.value.kebabCase())
                Response(Status.TEMPORARY_REDIRECT).with(LOCATION of request.uri.path(redirect))
            } else {
                kebabCaseConstituencyNames[constituencyName]?.let {
                    val page = ConstituencyPage(it)
                    Response(Status.OK).with(viewLens of page)
                } ?: Response(Status.NOT_FOUND)
            }
        }
    }
}

data class Coordinates(val lat: Float, val lon: Float)

class ConstituencyName(value: String) : StringValue(value) {
    companion object : StringValueFactory<ConstituencyName>(::ConstituencyName)
}

class MPName(value: String) : StringValue(value)
class PartyName(value: String) : StringValue(value)

data class PoliticalParty(
    val name: PartyName,
    val twitter: String
)

data class MP(
    val name: MPName,
    val party: PoliticalParty,
    val twitter: String,
)

data class Constituency(
    val name: ConstituencyName,
    val location: Coordinates,
    val mp: MP,
)

data class Constituencies(val constituencies: List<Constituency>)

interface PageViewModel : ViewModel {
    override fun template(): String {
        return "pages/${javaClass.simpleName}"
    }
}

data class ConstituencyPage(val name: ConstituencyName) : PageViewModel