package org.totp.pages

import org.http4k.core.Uri
import org.http4k.events.Event

data class IncomingHttpRequest(val uri: Uri, val status: Int, val duration: Long) : Event
data class OutgoingHttpRequest(val uri: Uri, val status: Int, val duration: Long) : Event

