package com.revline.tracker.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formats server ISO-8601 timestamps for the admin UI. */
object RelativeTime {

    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    private fun parse(iso: String?): Instant? =
        iso?.let { runCatching { Instant.parse(it) }.getOrNull() }

    /** "Active now" / "5 min ago" / "2 hours ago" / "3 days ago" / "Never". */
    fun lastSeen(iso: String?): String {
        val instant = parse(iso) ?: return "Never"
        val minutes = (Instant.now().toEpochMilli() - instant.toEpochMilli()) / 60_000L
        return when {
            minutes < 5 -> "Active now"
            minutes < 60 -> "$minutes min ago"
            minutes < 60 * 24 -> "${minutes / 60} hours ago"
            else -> "${minutes / (60 * 24)} days ago"
        }
    }

    /** Short date, e.g. "22 Jun 2026", or "—" if unparseable. */
    fun date(iso: String?): String {
        val instant = parse(iso) ?: return "—"
        return dateFormatter.format(instant.atZone(ZoneId.systemDefault()))
    }
}
