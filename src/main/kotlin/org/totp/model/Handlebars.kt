package org.totp.model

import org.http4k.core.Uri
import org.http4k.template.ViewModel


open class PageViewModel(val uri: Uri) : ViewModel {
    override fun template(): String {
        return "pages/${javaClass.simpleName}"
    }
}

