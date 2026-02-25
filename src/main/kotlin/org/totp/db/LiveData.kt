package org.totp.db

import java.time.Duration
import java.time.LocalDate
import kotlin.math.ceil


data class Bucket(
    val online: Duration,
    val offline: Duration,
    val start: Duration,
    val unknown: Duration,
    val potential: Duration
)

data class Thing(val p: String, val cid: String, val d: LocalDate, val a: String)


fun _key(c: String, td: Int): String {
    val s = (ceil((td / 3600.0) / 4.0) * 4.0).toInt()
    return "${c}-${s}"
}

fun codeFrom(total: Bucket): String {
    if (total.start > Duration.ZERO) return _key("o", total.start.seconds.toInt())
    if (total.potential > Duration.ZERO) return _key("p", total.potential.seconds.toInt())
    if (total.offline > Duration.ZERO) return _key("z", total.offline.seconds.toInt())
    if (total.unknown > Duration.ZERO) return _key("u", total.unknown.seconds.toInt())
    return _key("a", total.online.seconds.toInt())
}
