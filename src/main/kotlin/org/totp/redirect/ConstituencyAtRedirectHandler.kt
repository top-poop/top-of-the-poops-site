package org.totp.redirect

import org.http4k.core.*
import org.http4k.lens.Header
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.double
import org.totp.db.GeoLocation
import org.totp.db.ReferenceData
import org.totp.model.data.toSlug

class ConstituencyAtRedirectHandler(val referenceData: ReferenceData) : HttpHandler {

    val lat = Path.double().of("lat")
    val lon = Path.double().of("lon")
    val live = Query.optional("live")

    override fun invoke(request: Request): Response {

        return GeoLocation(lat = lat(request), lon = lon(request))
            .let { referenceData.constituencyAt(it) ?: referenceData.constituencyNear(it) }
            ?.toSlug()
            ?.let { slug ->
                val uri = live(request)?.let { Uri.of("/constituency/$slug/live") } ?: Uri.of("/constituency/$slug")

                Response(Status.TEMPORARY_REDIRECT).with(Header.LOCATION of uri)
            }
            ?: Response(Status.NOT_FOUND)
    }
}