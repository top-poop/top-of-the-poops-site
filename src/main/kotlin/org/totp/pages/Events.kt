package org.totp.pages

import org.http4k.core.Uri
import org.http4k.events.Event

data class ServerStartedEvent(val uri: Uri) : Event

