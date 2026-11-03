package com.shubham.spendtracker.ui

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Which dates the Payments list shows: everything, one calendar month, or a custom range (both ends included). */
sealed interface DateFilter {
    data object AllTime : DateFilter
    data class Month(val month: YearMonth) : DateFilter
    data class Range(val start: LocalDate, val end: LocalDate) : DateFilter

    /** The [from, until) millis this filter covers; null for all time. */
    fun window(zone: ZoneId = ZoneId.systemDefault()): LongRange? = when (this) {
        AllTime -> null
        is Month -> millis(month.atDay(1), zone) until millis(month.plusMonths(1).atDay(1), zone)
        is Range -> millis(start, zone) until millis(end.plusDays(1), zone)
    }

    fun label(): String = when (this) {
        AllTime -> "All time"
        is Month -> month.format(monthYearFmt)
        is Range -> when {
            start == end -> start.format(dayYearFmt)
            start.year == end.year -> "${start.format(dayMonthFmt)} – ${end.format(dayYearFmt)}"
            else -> "${start.format(dayYearFmt)} – ${end.format(dayYearFmt)}"
        }
    }

    /** Saved as text so the choice survives rotation and process death. */
    fun encode(): String = when (this) {
        AllTime -> "all"
        is Month -> "m:$month"
        is Range -> "r:$start:$end"
    }

    companion object {
        fun decode(s: String): DateFilter = runCatching {
            when {
                s.startsWith("m:") -> Month(YearMonth.parse(s.removePrefix("m:")))
                s.startsWith("r:") -> s.removePrefix("r:").split(":").let { (a, b) -> Range(LocalDate.parse(a), LocalDate.parse(b)) }
                else -> AllTime
            }
        }.getOrDefault(AllTime)

        private fun millis(d: LocalDate, zone: ZoneId) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        private val monthYearFmt = DateTimeFormatter.ofPattern("MMMM yyyy")
        private val dayMonthFmt = DateTimeFormatter.ofPattern("d MMM")
        private val dayYearFmt = DateTimeFormatter.ofPattern("d MMM yyyy")
    }
}
