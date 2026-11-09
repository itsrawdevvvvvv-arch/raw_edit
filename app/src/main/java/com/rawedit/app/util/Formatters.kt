package com.rawedit.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    fun duration(ms: Long): String {
        if (ms <= 0L) return "0:00"
        val total = ms / 1000
        val m = total / 60
        val s = total % 60
        return String.format(Locale.US, "%d:%02d", m, s)
    }

    fun bytes(b: Long): String {
        return when {
            b >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GB", b / (1024.0 * 1024.0 * 1024.0))
            b >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024.0))
            b >= 1024L -> String.format(Locale.US, "%.0f KB", b / 1024.0)
            else -> "$b B"
        }
    }

    fun date(epochMs: Long): String {
        return try {
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(epochMs))
        } catch (t: Throwable) {
            ""
        }
    }
}
