package org.totp.model

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Options
import com.github.jknack.handlebars.helper.StringHelpers
import org.http4k.core.Uri
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel
import org.http4k.urlEncoded


open class PageViewModel(val uri: Uri) : ViewModel {
    override fun template(): String {
        return "pages/${javaClass.simpleName}"
    }
}

object TotpHandlebars {
    fun templates() = HandlebarsTemplates(handlebarsConfiguration());

    fun handlebars() = handlebarsConfiguration()(Handlebars())

    private fun handlebarsConfiguration(): (Handlebars) -> Handlebars = {
        it.also {
            it.registerHelperMissing { _: Any, options: Options ->
                throw IllegalArgumentException(
                    "Missing value for: " + options.helperName
                )
            }
            StringHelpers.register(it)
            it.registerHelper("urlencode") { context: Any, _: Options -> context.toString().urlEncoded() }
            it.registerHelper("concat") { context: Any, options -> (listOf(context) + options.params).joinToString("") }
        }
    }

}