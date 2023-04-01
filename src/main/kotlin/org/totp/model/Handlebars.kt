package org.totp.model

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Options
import com.github.jknack.handlebars.helper.StringHelpers
import org.http4k.core.Uri
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel
import org.http4k.urlEncoded
import java.text.NumberFormat


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
            StringHelpers.join.registerHelper(it)
            StringHelpers.dateFormat.registerHelper(it)
            it.registerHelper("numberFormat") { context: Any, _: Options ->
                if (context is Number) {
                    NumberFormat.getNumberInstance().format(context)
                } else {
                    throw IllegalArgumentException("Wrong type for context")
                }
            }
            it.registerHelper("inc") { context: Any, _: Options -> context.toString().toInt() + 1 }
            it.registerHelper("maybe") { context: Any?, _: Options -> context?.let { it.toString() } ?: "" }
            it.registerHelper("urlencode") { context: Any, _: Options -> context.toString().urlEncoded() }
            it.registerHelper("concat") { context: Any, options -> (listOf(context) + options.params).joinToString("") }
        }
    }
}