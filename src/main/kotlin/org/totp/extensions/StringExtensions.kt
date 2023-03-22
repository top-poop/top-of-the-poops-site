package org.totp.extensions

import org.apache.commons.lang3.StringUtils


val kebab = "[A-Z]{2,}(?=[A-Z][a-z]+[0-9]*|\b)|[A-Z]?[a-z]+[0-9]*|[A-Z]|[0-9]+".toRegex()

fun String.kebabCase(): String {
    return kebab.findAll(StringUtils.stripAccents(lowercase())).map { it.groups[0]?.value }.joinToString("-")
}

