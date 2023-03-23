package org.totp.data

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
import org.totp.http4k.StandardFilters
import org.totp.model.data.objectMapper
import org.totp.pages.Html
import java.io.IOException
import java.time.Clock


class Bob

fun main() {
    val events =
        EventFilters.AddTimestamp(Clock.systemUTC())
            .then(EventFilters.AddEventName())
            .then(EventFilters.AddServiceName("pages"))
            .then(AutoMarshallingEvents(Jackson))

    val mediaJson = Bob::class.java.getResource("/data/media-appearances.json").readText()

    val okHttpClient = StandardFilters.outgoing(events)
        .then(ClientFilters.FollowRedirects())
        .then(OkHttp())

    val uris = objectMapper.readerForListOf(HashMap::class.java)
        .readValue<List<Map<String, Any?>>>(mediaJson)
        .map {

            try {
                val uri = Uri.of(it["href"] as String)
                val image = it["image"] as String?

                if ( image == null ) {

                    val html = okHttpClient(Request(Method.GET, uri))

                    val og = Html(html).select("meta[name='twitter:image']").firstOrNull()?.let { e ->
                        Uri.of(e.attr("content"))
                    }

                    uri to og
                }
            }
            catch (e: IOException) {
                print(e)
            }
        }

    print("")
    print(uris.joinToString("\n"))
}
