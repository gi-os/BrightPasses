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
}
