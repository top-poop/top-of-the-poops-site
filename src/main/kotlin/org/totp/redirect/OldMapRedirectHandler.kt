package org.totp.redirect

import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.with
import org.http4k.lens.Header
import org.http4k.lens.Query
import org.http4k.lens.value
import org.totp.model.data.ConstituencyName
import org.totp.model.data.toSlug
import org.totp.pages.constituencyNames

object OldMapRedirectHandler {
    operator fun invoke(): HttpHandler {

        val constituency = Query.value(ConstituencyName).optional("c")

        return { request ->
            val selected = constituency(request)

            if (selected != null && constituencyNames.contains(selected)) {
                val slug = selected.toSlug()
                Response(Status.TEMPORARY_REDIRECT).with(Header.LOCATION of Uri.of("/constituency/$slug"))
            } else {
                Response(Status.TEMPORARY_REDIRECT).with(Header.LOCATION of Uri.of("/constituencies"))
            }
        }
    }
}