package org.totp.redirect

import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.with
import org.http4k.lens.Header
import org.http4k.lens.Path
import org.http4k.lens.value
import org.totp.model.data.Slug

class LocalityPlaceRedirectHandler: HttpHandler {
    val locality = Path.value(Slug).of("locality")
    override fun invoke(request: Request): Response {
        val l = locality(request)
        return Response(Status.PERMANENT_REDIRECT).with(Header.LOCATION of Uri.of("/place/${l.value}"))
    }
}