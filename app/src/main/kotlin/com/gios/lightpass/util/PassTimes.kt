package com.gios.lightpass.util

import com.gios.lightpass.data.PassEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object PassTimes {
    private const val DEFAULT_RUNTIME_MIN = 180L
    private val timeFmts = listOf("h:mm a", "hh:mm a", "H:mm").map {
        DateTimeFormatter.ofPattern(it, Locale.US)
    }
    private val outFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM", Locale.US)

    fun start(pass: PassEntity): LocalDateTime? {
        val d = pass.date ?: return null
        val t = pass.time ?: return null
        val date = runCatching { LocalDate.parse(d) }.getOrNull() ?: return null
        val time = timeFmts.firstNotNullOfOrNull { runCatching { LocalTime.parse(t.trim().uppercase(Locale.US), it) }.getOrNull() }
            ?: return null
        return LocalDateTime.of(date, time)
    }

    fun endMillis(pass: PassEntity): Long? {
        val s = start(pass) ?: return null
        val mins = pass.runtimeMin?.toLong() ?: DEFAULT_RUNTIME_MIN
        return s.plusMinutes(mins).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun isArchived(pass: PassEntity, now: Long = System.currentTimeMillis()): Boolean {
        val end = endMillis(pass) ?: return false
        return now > end
    }

    fun beginsLabel(pass: PassEntity): String? = start(pass)?.format(outFmt)
    fun endsLabel(pass: PassEntity): String? {
        val s = start(pass) ?: return null
        val mins = pass.runtimeMin?.toLong() ?: return null   // only show end when runtime known
        return s.plusMinutes(mins).format(outFmt)
    }

    fun startMillis(pass: PassEntity): Long? =
        start(pass)?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

    /** "2026-08-06" -> "August 6th" */
    fun humanDate(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        val d = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
        val day = d.dayOfMonth
        val suffix = when {
            day in 11..13 -> "th"
            day % 10 == 1 -> "st"
            day % 10 == 2 -> "nd"
            day % 10 == 3 -> "rd"
            else -> "th"
        }
        return "${d.format(monthFmt)} $day$suffix"
    }
}

object ShowTime {
    private val re = Regex("""^\s*(\d{1,2}):(\d{2})\s*([AaPp])\.?[Mm]\.?\s*$""")
    /** Correct obvious AM/PM slips: a 1:00-6:59 AM showtime is really PM. */
    fun normalize(time: String?): String? {
        val t = time?.trim() ?: return time
        val m = re.find(t) ?: return time
        val hour = m.groupValues[1].toInt()
        val min = m.groupValues[2]
        var mer = m.groupValues[3].uppercase()
        if (mer == "A" && hour in 1..6) mer = "P"   // no movie starts 1-6 AM
        val h = if (hour == 0) 12 else hour
        return "$h:$min ${mer}M"
    }
}
