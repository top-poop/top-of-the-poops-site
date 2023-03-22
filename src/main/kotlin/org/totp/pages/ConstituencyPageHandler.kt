package org.totp.pages

import dev.forkhandles.values.StringValue
import dev.forkhandles.values.StringValueFactory
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.lens.Header.LOCATION
import org.http4k.lens.Path
import org.http4k.lens.value
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.extensions.kebabCase
import org.totp.model.PageViewModel
import org.totp.model.data.CSOTotals
import org.totp.model.data.ConstituencyName
import org.totp.model.data.GeoJSON
import org.totp.text.csv.readCSV
import java.time.Duration


val constituencyNames = readCSV(
    resource = "/data/constituencies.csv",
    mapper = { ConstituencyName(it[0]) }
).toSortedSet(Comparator.comparing { it.value })

class ConstituencySlug(value: String) : StringValue(value) {
    companion object : StringValueFactory<ConstituencySlug>(::ConstituencySlug) {
        fun from(name: ConstituencyName): ConstituencySlug {
            return of(name.value.kebabCase())
        }
    }
}

val slugToConstituency = constituencyNames.associateBy { ConstituencySlug.from(it) }

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
                companies = csos
                    .map { it.cso.company }
                    .toSet()
                    .toList()
                    .sorted(),
                count = csos
                    .filter { it.duration > Duration.ZERO }
                    .sumOf { it.count },
                duration = csos
                    .map { it.duration }
                    .reduceOrNull() { acc, duration -> acc.plus(duration) }
                    ?: Duration.ZERO
            )
        }
    }
}

data class RenderableConstituency(val name: String, val current: Boolean, val uri: Uri)


class ConstituencyPage(
    uri: Uri,
    val name: ConstituencyName,
    val summary: ConstituencySummary,
    val geojson: GeoJSON,
    val csos: List<CSOTotals>,
    val constituencies: List<RenderableConstituency>,
) :
    PageViewModel(uri)


object ConstituencyRedirectFilter {
    operator fun invoke(): Filter {
        val constituencyName = Path.value(ConstituencyName).of("constituency", "The constituency")

        return Filter { next ->
            { request ->
                val suppliedName = constituencyName(request)
                if (constituencyNames.contains(suppliedName)) {
                    val redirect = request.uri.path.replace(suppliedName.value, suppliedName.value.kebabCase())
                    Response(Status.TEMPORARY_REDIRECT)
                        .with(
                            LOCATION of request.uri.path(redirect)
                        )
                } else {
                    next(request)
                }
            }
        }
    }
}

object ConstituencyPageHandler {
    operator fun invoke(
        renderer: TemplateRenderer,
        constituencySpills: (ConstituencyName) -> List<CSOTotals>,
        constituencyBoundary: (ConstituencyName) -> GeoJSON
    ): HttpHandler {
        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        val constituencySlug = Path.value(ConstituencySlug).of("constituency", "The constituency")

        return ConstituencyRedirectFilter().then { request: Request ->
            val slug = constituencySlug(request)

            slugToConstituency[slug]?.let { constituencyName ->

                val renderableConstituencies = slugToConstituency
                    .map {
                        RenderableConstituency(
                            name = it.value.value,
                            current = it.key == slug,
                            uri = Uri.of("/constituency/" + it.key)
                        )
                    }

                val list = constituencySpills(constituencyName)
                Response(Status.OK)
                    .with(
                        viewLens of ConstituencyPage(
                            request.uri,
                            constituencyName,
                            ConstituencySummary.from(list),
                            constituencyBoundary(constituencyName),
                            list.sortedByDescending { it.duration },
                            renderableConstituencies,
                        )
                    )
            }
                ?: Response(Status.NOT_FOUND)
        }
    }
}
