package org.totp.pages

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Options
import com.github.jknack.handlebars.helper.StringHelpers
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.with
import org.http4k.lens.Header.LOCATION
import org.http4k.lens.Path
import org.http4k.lens.value
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.viewModel
import org.http4k.urlEncoded
import org.totp.extensions.kebabCase
import org.totp.model.PageViewModel
import org.totp.model.data.CSOTotals
import org.totp.model.data.ConstituencyName
import org.totp.model.data.GeoJSON
import org.totp.text.csv.readCSV
import java.time.Duration


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
        it.registerHelper("concat") { context: Any, options -> (listOf(context) + options.params).joinToString("")}
    }
}

data class ConstituencySummary(
    val year: Int,
    val locationCount: Int,
    val companies: List<String>,
    val count: Int,
    val duration: Duration
) {

    companion object {
        fun from(csos: List<CSOTotals>): ConstituencySummary {
            return ConstituencySummary(
                year = 2021,
                locationCount = csos.size,
                companies = csos.map { it.cso.company }.toSet().toList().sorted(),
                count = csos.filter { it.duration > Duration.ZERO }.sumOf { it.count },
                duration = csos.map { it.duration }.reduce { acc, duration -> acc.plus(duration) }
            )
        }
    }
}

class ConstituencyPage(
    uri: Uri,
    val name: ConstituencyName,
    val summary: ConstituencySummary,
    val geojson: GeoJSON,
    val csos: List<CSOTotals>
) :
    PageViewModel(uri)

object ConstituencyPageHandler {
    operator fun invoke(
        constituencySpills: (ConstituencyName) -> List<CSOTotals>,
        constituencyBoundary: (ConstituencyName) -> GeoJSON
    ): HttpHandler {
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
                kebabCaseConstituencyNames[constituencyName]?.let { name ->
                    val list = constituencySpills(name)
                    Response(Status.OK)
                        .with(
                            viewLens of ConstituencyPage(
                                request.uri,
                                name,
                                ConstituencySummary.from(list),
                                constituencyBoundary(constituencyName),
                                list.sortedByDescending { it.duration }
                            )
                        )
                } ?: Response(Status.NOT_FOUND)
            }
        }
    }
}
