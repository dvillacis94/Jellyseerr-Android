package com.nesoi.seerr.android.media

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object MediaFormat {
    /** "2h 15m", or "45m". `null` when there is no length. */
    fun runtime(minutes: Int?): String? {
        if (minutes == null || minutes <= 0) return null
        val hours = minutes / 60
        val rest = minutes % 60
        return if (hours > 0) "${hours}h ${rest}m" else "${rest}m"
    }

    /** A TMDB score as "7.8", or `null` when nobody has voted. */
    fun rating(vote: Double?): String? = vote?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f", it) }

    /** "2019-04-26" as "Apr 26, 2019" for the locale. Anything unparseable gives `null`. */
    fun date(iso: String?, locale: Locale = Locale.getDefault()): String? {
        val day = iso?.takeIf { it.length >= 10 }?.substring(0, 10) ?: return null
        return runCatching { LocalDate.parse(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }
            .getOrNull()
    }

    fun year(iso: String?): String? = iso?.takeIf { it.length >= 4 }?.substring(0, 4)
}
