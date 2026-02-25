package org.totp.extensions

import java.time.Duration


fun <T> Iterable<T>.sumDuration(f: (T) -> Duration): Duration = this.map(f).fold(Duration.ZERO) { acc, item -> acc + item }