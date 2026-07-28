package com.gios.lightpass.util

import java.util.Locale

object TextUtils {
    /** METROGRAPH / metrograph -> Metrograph, but keep short all-caps acronyms (AMC, IFC, BAM). */
    fun titleCaseVenue(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isBlank()) return null
        return s.split(Regex("\\s+")).joinToString(" ") { word ->
            val letters = word.filter { it.isLetter() }
            when {
                letters.length in 1..3 && word == word.uppercase(Locale.US) -> word // AMC, IFC, BAM
                else -> word.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
            }
        }
    }
}
