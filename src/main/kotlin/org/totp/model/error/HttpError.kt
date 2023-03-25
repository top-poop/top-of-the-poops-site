package org.totp.model.error

import org.http4k.core.Status
import org.http4k.template.ViewModel

data class HttpError(val status: Status, val t: Throwable? = null) : ViewModel {
    override fun template(): String {

        val specific = when (status) {
            Status.NOT_FOUND, Status.INTERNAL_SERVER_ERROR -> status.code.toString()
            else -> "generic"
        }

        return "model/error/${specific}"
    }
}