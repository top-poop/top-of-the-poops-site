package org.totp.events

import org.http4k.core.Uri
import org.http4k.events.Event

data class ServerStartedEvent(
    val uri: Uri,
    val isDevelopmentMode: Boolean
) : Event

