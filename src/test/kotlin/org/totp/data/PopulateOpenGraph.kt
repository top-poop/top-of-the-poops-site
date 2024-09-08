package org.totp.data

import com.fasterxml.jackson.module.kotlin.readValue
import org.http4k.client.OkHttp
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.events.AutoMarshallingEvents
import org.http4k.events.EventFilters
import org.http4k.events.then
import org.http4k.filter.ClientFilters
import org.http4k.format.Jackson
import org.http4k.format.Jackson.asJsonObject
import org.jsoup.nodes.Element
import org.totp.http4k.StandardFilters
import org.totp.model.data.TotpJson
import org.totp.pages.Html
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId


data class BareMediaAppearance(
    val href: Uri,
    val where: String?,
    val title: String?,
    val date: LocalDate?,
    val image: Uri?
)

class Selector<T>(
    val selector: String,
    val action: (BareMediaAppearance, T) -> BareMediaAppearance,
    val extractor: (Element) -> T
) {
    fun apply(e: Element, bm: BareMediaAppearance): BareMediaAppearance {
        return extractor(e)?.let { action(bm, it) }?:bm
    }
}

fun main() {
    val events =
        EventFilters.AddTimestamp(Clock.systemUTC())
            .then(EventFilters.AddEventName())
            .then(EventFilters.AddServiceName("pages"))
            .then(AutoMarshallingEvents(Jackson))

    val file = File("./services/data/provided/media-appearances.json")

    val okHttpClient = StandardFilters.outgoing(events)
        .then(ClientFilters.FollowRedirects())
        .then(OkHttp())

    val queries = listOf(
        Selector("meta[name='twitter:image']", { m, s -> m.copy(image = Uri.of(s)) }, { it.text() }),
        Selector("meta[property='article:published']", { m, s -> m.copy(date = s) }, { LocalDate.ofInstant(Instant.parse(it.attr("content")), ZoneId.of("UTC")) }),
        Selector("meta[property='article:published_time']", { m, s -> m.copy(date = s) }, { LocalDate.ofInstant(Instant.parse(it.attr("content")), ZoneId.of("UTC")) }),
        Selector("meta[property='og:site_name']", { m, s -> m.copy(where = s) }, { it.attr("content") }),
        Selector("meta[property='article:publication']", { m, s -> m.copy(where = s) }, { it.attr("content") }),
        Selector("meta[property='og:title']", { m, s -> m.copy(title = s) }, { it.attr("content") }),
        Selector("meta[property='og:image']", { m, s -> m.copy(image = Uri.of(s)) }, { it.attr("content") }),
        Selector("meta[property='og:image:url']", { m, s -> m.copy(image = Uri.of(s)) }, { it.attr("content") }),
        Selector("link[rel='canonical']", { m, s -> m.copy(href = Uri.of(s)) }, { it.attr("href") })
    )

    val results: List<BareMediaAppearance> = TotpJson.mapper.readValue<List<BareMediaAppearance>>(file.readText())
        .map { appearance ->
            try {
                if (appearance.image == null) {
                    println("Getting ${appearance.href}")
                    val response = okHttpClient(Request(Method.GET, appearance.href))

                    if (response.status.successful) {
                        val document = Html(response)

                        queries.fold(appearance) { acc, selector ->
                            document
                                .select(selector.selector)
                                .firstOrNull()
                                ?.let { selector.apply(it, acc) }
                                ?: acc
                        }
                    } else {
                        println("${response.status.code}: ${appearance.href}")
                        appearance
                    }
                } else {
                    appearance
                }
            } catch (e: IOException) {
                print(e)
                appearance
            }
        }


    file.writeText(results.asJsonObject().toPrettyString())
}
