package org.totp.db

import java.time.Duration
import java.time.temporal.Temporal
import java.time.temporal.TemporalUnit

class DurationUnit private constructor(duration: Duration) : TemporalUnit {
    private val duration: Duration

    init {
        require(!(duration.isZero() || duration.isNegative())) { "Duration may not be zero or negative" }
        this.duration = duration
    }

    override fun getDuration(): Duration {
        return this.duration
    }

    override fun isDurationEstimated(): Boolean {
        return (duration.seconds >= SECONDS_PER_DAY)
    }

    override fun isDateBased(): Boolean {
        return (duration.nano == 0 && duration.getSeconds() % SECONDS_PER_DAY == 0L)
    }

    override fun isTimeBased(): Boolean {
        return (duration.seconds < SECONDS_PER_DAY && (NANOS_PER_DAY % duration.toNanos() == 0L))
    }

    override fun <R : Temporal> addTo(temporal: R, amount: Long): R {
        return duration.multipliedBy(amount).addTo(temporal) as R
    }

    override fun between(temporal1Inclusive: Temporal, temporal2Exclusive: Temporal): Long {
        return Duration.between(temporal1Inclusive, temporal2Exclusive).dividedBy(this.duration)
    }

    override fun toString(): String {
        return duration.toString()
    }

    companion object {
        private const val SECONDS_PER_DAY = 86400
        private const val NANOS_PER_SECOND = 1000000000L
        private const val NANOS_PER_DAY = NANOS_PER_SECOND * SECONDS_PER_DAY

        fun of(duration: Duration): DurationUnit {
            return DurationUnit(duration)
        }

        fun ofDays(days: Long): DurationUnit {
            return DurationUnit(Duration.ofDays(days))
        }

        fun ofHours(hours: Long): DurationUnit {
            return DurationUnit(Duration.ofHours(hours))
        }

        fun ofMinutes(minutes: Long): DurationUnit {
            return DurationUnit(Duration.ofMinutes(minutes))
        }

        fun ofSeconds(seconds: Long): DurationUnit {
            return DurationUnit(Duration.ofSeconds(seconds))
        }

        fun ofMillis(millis: Long): DurationUnit {
            return DurationUnit(Duration.ofMillis(millis))
        }

        fun ofNanos(nanos: Long): DurationUnit {
            return DurationUnit(Duration.ofNanos(nanos))
        }
    }
}