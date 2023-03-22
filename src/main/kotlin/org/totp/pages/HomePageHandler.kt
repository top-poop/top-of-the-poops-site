package org.totp.pages

import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.with
import org.http4k.template.TemplateRenderer
import org.http4k.template.viewModel
import org.totp.model.PageViewModel
import org.totp.model.data.ConstituencyName
import java.time.Duration

data class MP(val name: String, val party: String, val handle: String?, val uri: Uri)

data class ConstituencyRank(
    val rank: Int,
    val constituencyName: ConstituencyName,
    val constituencyUri: Uri,
    val mp: MP,
    val company: String,
    val count: Int,
    val duration: Duration,
    val countDelta: Int,
    val durationDelta: Duration
)

@Suppress("unused")
class HomePage(uri: Uri, val rankings: List<ConstituencyRank>) : PageViewModel(uri)

object HomepageHandler {

    operator fun invoke(
        renderer: TemplateRenderer,
        rankings: () -> List<ConstituencyRank>
    ): HttpHandler {

        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request ->
            Response(Status.OK)
                .with(
                    viewLens of HomePage(
                        request.uri,
                        rankings(),
                    )
                )
        }
    }
}

