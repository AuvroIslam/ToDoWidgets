package com.simpletodo.data

import java.time.Instant
import java.time.ZoneId

/**
 * The calendar behind lists that start fresh every morning.
 *
 * A "day" here runs from 4 am to 4 am rather than from midnight, so that someone ticking off their
 * evening routine at ten past twelve does not watch it wipe itself as they go. Four is late enough
 * to count as last night and early enough to be done before anybody's morning.
 */
object DailyReset {

    const val RESET_HOUR = 4

    /** The day [nowMillis] falls on, as a count of days: the value lists are stamped with. */
    fun dayOf(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).minusHours(RESET_HOUR.toLong()).toLocalDate().toEpochDay()

    /** How long from [nowMillis] until the next reset moment, in milliseconds. Always positive. */
    fun millisUntilNext(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        var next = now.toLocalDate().atTime(RESET_HOUR, 0).atZone(zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.toInstant().toEpochMilli() - nowMillis
    }
}
