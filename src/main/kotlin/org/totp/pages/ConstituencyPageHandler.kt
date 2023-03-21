package org.totp.pages

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.jknack.handlebars.Handlebars
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
import java.time.Duration
import kotlin.io.path.bufferedReader


val rawConstituencyNames = readCSV(
    resource = "/data/constituencies.csv",
    mapper = { ConstituencyName(it[0]) }
).toSet()

val kebabCaseConstituencyNames = rawConstituencyNames.associateBy {
    ConstituencyName(it.value.kebabCase())
}

fun handlebarsConfiguration(): (Handlebars) -> Handlebars = {
    it.also {
        it.registerHelperMissing { _: Any, options: Options ->
            throw IllegalArgumentException(
                "Missing value for: " + options.helperName
            )
        }
        StringHelpers.register(it)
        it.registerHelper("urlencode") { context: Any, _: Options -> context.toString().urlEncoded() }
    }
}


object ConstituencyPageHandler {
    operator fun invoke(csos: (ConstituencyName) -> List<CSOTotals>): HttpHandler {
        val renderer = HandlebarsTemplates(handlebarsConfiguration())
            .HotReload("src/main/resources/templates/page/org/totp")
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        val constituency = Path.value(ConstituencyName).of("constituency", "The constituency")

        return { request: Request ->
            val constituencyName = constituency(request)

            if (rawConstituencyNames.contains(constituencyName)) {
                val redirect = request.uri.path.replace(constituencyName.value, constituencyName.value.kebabCase())
                Response(Status.TEMPORARY_REDIRECT)
                    .with(
                        LOCATION of request.uri.path(redirect)
                    )
            } else {
                kebabCaseConstituencyNames[constituencyName]?.let {
                    val list = csos(it)
                    Response(Status.OK)
                        .with(
                            viewLens of ConstituencyPage(
                                it,
                                ConstituencySummary.from(list),
                                list.sortedByDescending { it.duration }
                            )
                        )
                } ?: Response(Status.NOT_FOUND)
            }
        }
    }
}

data class ConstituencySummary(
    val locationCount: Int,
    val companies: List<String>,
    val count: Int,
    val duration: Duration
) {

    companion object {
        fun from(csos: List<CSOTotals>): ConstituencySummary {
            return ConstituencySummary(
                locationCount = csos.size,
                companies = csos.map { it.cso.company }.toSet().toList().sorted(),
                count = csos.filter { it.duration > Duration.ZERO }.sumOf { it.count },
                duration = csos.map { it.duration }.reduce { acc, duration -> acc.plus(duration) }
            )
        }
    }
}


fun csoSummaries(location: java.nio.file.Path): (ConstituencyName) -> List<CSOTotals> {
    val objectMapper = ObjectMapper()
    val list = location.bufferedReader().let {
        objectMapper.readerForListOf(HashMap::class.java).readValue<List<Map<String, Any>>>(it)
    }.map {
        CSOTotals(
            constituency = ConstituencyName(it["constituency"].toString()),
            cso = CSO(
                company = it["company_name"].toString(),
                sitename = it["site_name"].toString(),
                waterway = it["receiving_water"].toString(),
                location = Coordinates(
                    lat = it["lat"] as Double,
                    lon = it["lon"] as Double
                )
            ),
            count = (it["spill_count"] as Double).toInt(),
            duration = Duration.ofHours((it["total_spill_hours"] as Double).toLong()),
            reporting = it["reporting_percent"] as Double
        )
    }

    return { name -> list.filter { it.constituency == name } }
}


data class CSOTotals(
    val constituency: ConstituencyName, val cso: CSO, val count: Int, val duration: Duration, val reporting: Number
)

data class CSO(val company: String, val sitename: String, val waterway: String, val location: Coordinates)

data class Coordinates(val lat: Number, val lon: Number)

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

data class ConstituencyPage(
    val name: ConstituencyName,
    val summary: ConstituencySummary,
    val csos: List<CSOTotals>
) :
    PageViewModel