package org.totp.model

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Options
import com.github.jknack.handlebars.helper.ConditionalHelpers
import com.github.jknack.handlebars.helper.StringHelpers
import org.http4k.core.Uri
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel
import org.http4k.urlEncoded
import org.ocpsoft.prettytime.PrettyTime
import java.text.NumberFormat
import java.time.Instant


open class PageViewModel(val uri: Uri) : ViewModel {
    override fun template(): String {
        return "pages/${javaClass.simpleName}"
    }
}

open class FragmentViewModel : ViewModel {
    override fun template(): String {
        return "fragments/${javaClass.simpleName}"
    }
}

object TotpHandlebars {
    fun templates() = HandlebarsTemplates(handlebarsConfiguration())

    fun handlebars() = handlebarsConfiguration()(Handlebars())

    private fun handlebarsConfiguration(): (Handlebars) -> Handlebars = {
        it.also {
            it.registerHelperMissing { _: Any, options: Options ->
                throw IllegalArgumentException(
                    "Missing value for: " + options.helperName
                )
            }
            it.registerHelper("ago") { context: Any, _: Options ->
                if (context is Instant) {
                    PrettyTime(Instant.now()).format(context)
                } else {
                    throw IllegalArgumentException("Expected instant not $context")
                }
            }
            StringHelpers.join.registerHelper(it)
            StringHelpers.dateFormat.registerHelper(it)
            it.registerHelpers(ConditionalHelpers::class.java)
            it.registerHelper("numberFormat1") { context: Any, _: Options ->
                if (context is Number) {
                    numberFormat(digits=1)(context)
                } else {
                    throw IllegalArgumentException("Wrong type for context")
                }
            }
            it.registerHelper("numberFormat0") { context: Any, _: Options ->
                if (context is Number) {
                    numberFormat(digits=0)(context)
                } else {
                    throw IllegalArgumentException("Wrong type for context")
                }
            }
            it.registerHelper("numberFormat") { context: Any, _: Options ->
                if (context is Number) {
                    numberFormat(digits=2)(context)
                } else {
                    throw IllegalArgumentException("Wrong type for context")
                }
            }
            it.registerHelper("inc") { context: Any, _: Options -> context.toString().toInt() + 1 }
            it.registerHelper("maybe") { context: Any?, _: Options -> context?.let { it.toString() } ?: "" }
            it.registerHelper("urlencode") { context: Any, _: Options -> context.toString().urlEncoded() }
            it.registerHelper("concat") { context: Any, options -> (listOf(context) + options.params).joinToString("") }
            it.registerHelper("take") { context: Any, options -> (context as Iterable<*>).take(options.hash("n")) }
            it.registerHelper("truncate") { context: Any, options -> (context as String).take(options.hash("n")) }
            it.registerHelper("highlight") { context: Any, options: Options ->
                val text = context.toString()
                val word = options.hash<String>("h")
                if (word.isNullOrBlank()) {
                    text
                } else {
                    val pattern = Regex(word, RegexOption.IGNORE_CASE)
                    pattern.replace(text) { "<mark>${it.value}</mark>" }
                }
            }
        }
    }

    fun numberFormat(digits: Int = 2): (Number) -> String {
        val nf = NumberFormat.getNumberInstance().also {
            it.maximumFractionDigits = digits
        }
        return { nf.format(it) }
    }
}