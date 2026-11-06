package com.assistant.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The exchanges of one local day. [fromMillis] and [toMillis] are the day's first and last millisecond, both inclusive. */
data class DayGroup(val date: LocalDate, val fromMillis: Long, val toMillis: Long, val turns: List<StoredTurn>)

/**
 * Groups exchanges (newest first) into local days for the Past conversations list: days newest first, and inside a day
 * the exchanges in the order they happened.
 */
fun groupByDay(turnsNewestFirst: List<StoredTurn>, zone: ZoneId = ZoneId.systemDefault()): List<DayGroup> =
    turnsNewestFirst.groupBy { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() }
        .map { (date, turns) ->
            DayGroup(
                date = date,
                fromMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                toMillis = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
                turns = turns.sortedBy { it.timestampMillis }
            )
        }
        .sortedByDescending { it.date }
