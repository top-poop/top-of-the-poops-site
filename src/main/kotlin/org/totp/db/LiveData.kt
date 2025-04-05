package org.totp.db

import java.time.LocalDate
import kotlin.math.ceil


data class Bucket(
    val online: Int,
    val offline: Int,
    val overflowing: Int,
    val unknown: Int,
    val potentially_overflowing: Int
)

data class Thing(val p: String, val cid: String, val d: LocalDate, val a: String)


fun _key(c: String, td: Int): String {
    val s = (ceil((td / 3600.0) / 4.0) * 4.0).toInt()
    return "${c}-${s}"
}

fun codeFrom(total: Bucket): String {
    if (total.overflowing > 0) return _key("o", total.overflowing)
    if (total.potentially_overflowing > 0) return _key("p", total.potentially_overflowing)
    if (total.offline > 0) return _key("z", total.offline)
    if (total.unknown > 0) return _key("u", total.unknown)
    return _key("a", total.online)
}
