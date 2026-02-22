package org.totp.pages

import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.lens.Path
import org.totp.db.StreamData
import org.totp.model.data.Coordinates
import org.totp.model.data.RenderableCompany
import org.totp.model.data.SiteName
import org.totp.model.data.TotpJson
import org.totp.model.data.WaterwayName
import org.totp.model.data.toRenderable
import java.time.Clock


data class StreamAssetBoundingBoxResult(
    val id: RenderableStreamId,
    val company: RenderableCompany,
    val constituency: RenderableConstituency,
    val loc: Coordinates,
    val siteName: SiteName,
    val receivingWater: WaterwayName
)

class StreamAssetsBoundingBoxHandler(val clock: Clock, val stream: StreamData): HttpHandler {

    private val geopath = Path.map(nextIn = { s ->
        s.split(",").map { it.toDouble() }.take(2).let { Coordinates(it.first(), it.last()) }
    }, nextOut = { "${it.lat},${it.lon}" })

    private val ne = geopath.of("ne")
    private val sw = geopath.of("sw")

    private val jsonResponse = TotpJson.autoBody<List<StreamAssetBoundingBoxResult>>().toLens()

    override fun invoke(request: Request): Response {

        val ne = ne(request)
        val sw = sw(request)

        return Response(Status.OK).with(jsonResponse of stream.csosWithin(ne,sw).map {
            StreamAssetBoundingBoxResult(
                id = it.id.toRenderable(),
                company = it.company.toRenderable(),
                constituency = it.pcon24nm.toRenderable(false, true),
                loc = it.loc,
                siteName = it.site_name,
                receivingWater = it.receiving_water,
            )
        })
    }
}
