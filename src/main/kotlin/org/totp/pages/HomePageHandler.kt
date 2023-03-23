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
import org.totp.http4k.pageUriFrom
import org.totp.model.PageViewModel
import org.totp.model.data.ConstituencyName
import org.totp.model.data.MediaAppearance
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
class HomePage(
    uri: Uri,
    val rankings: List<ConstituencyRank>,
    val appearances: List<MediaAppearance>,
    val share: SocialShare
) : PageViewModel(uri)

object HomepageHandler {

    operator fun invoke(
        renderer: TemplateRenderer,
        rankings: () -> List<ConstituencyRank>,
        appearances: () -> List<MediaAppearance>
    ): HttpHandler {

        val viewLens = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()

        return { request ->
            Response(Status.OK)
                .with(
                    viewLens of HomePage(
                        pageUriFrom(request),
                        rankings().take(10),
                        appearances().sortedByDescending { it.date }.take(9),
                        SocialShare(
                            pageUriFrom(request),
                            "Water companies are dumping #sewage into rivers and bathing areas all over the UK - over 470,000 times in 2021 - it needs to be stopped",
                            listOf("sewage"),
                            via = "@sewageuk"
                        )
                    )
                )
        }
    }
}

