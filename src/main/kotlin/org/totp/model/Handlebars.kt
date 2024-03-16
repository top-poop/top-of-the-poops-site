package org.totp.model

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Options
import com.github.jknack.handlebars.helper.StringHelpers
import org.http4k.core.Uri
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel
import org.http4k.urlEncoded
import org.ocpsoft.prettytime.PrettyTime
import java.text.NumberFormat
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.Temporal
import java.time.temporal.TemporalAccessor


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
            it.registerHelper("ago") { context: Any, _: Options ->
                if ( context is Instant) {
                    PrettyTime(Instant.now()).format(context)
                }
                else {
                    throw IllegalArgumentException("Expected instant not $context")
                }
            }
            StringHelpers.join.registerHelper(it)
            StringHelpers.dateFormat.registerHelper(it)
            it.registerHelper("numberFormat1") { context: Any, _: Options ->
                if (context is Number) {
                    val instance = NumberFormat.getNumberInstance()
                    instance.also {
                        it.maximumFractionDigits = 1
                    }
                        .format(context)
                } else {
                    throw IllegalArgumentException("Wrong type for context")
                }
            }
            it.registerHelper("numberFormat") { context: Any, _: Options ->
                if (context is Number) {
                    numberFormat()(context)
                } else {
                    throw IllegalArgumentException("Wrong type for context")
                }
            }
            it.registerHelper("inc") { context: Any, _: Options -> context.toString().toInt() + 1 }
            it.registerHelper("maybe") { context: Any?, _: Options -> context?.let { it.toString() } ?: "" }
            it.registerHelper("urlencode") { context: Any, _: Options -> context.toString().urlEncoded() }
            it.registerHelper("concat") { context: Any, options -> (listOf(context) + options.params).joinToString("") }
            it.registerHelper("take") { context: Any, options -> (context as Iterable<*>).take(options.hash<Int?>("n"))}
        }
    }

    fun numberFormat(): (Number) -> String {
        val nf = NumberFormat.getNumberInstance().also {
            it.maximumFractionDigits = 2
        }
        return { nf.format(it) }
    }
}