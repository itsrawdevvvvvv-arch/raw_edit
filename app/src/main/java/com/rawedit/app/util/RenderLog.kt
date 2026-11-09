package com.rawedit.app.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A plain text log of the last render. Without a device we cannot see what the
 * WebView or the encoder actually did, so the app writes it down and the user can
 * share the file from the error screen.
 */
object RenderLog {

    private var target: File? = null
    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun start(context: Context) {
        try {
            val f = File(context.filesDir, "render_log.txt")
            f.writeText("raw_edit render log\n")
            target = f
        } catch (t: Throwable) {
            target = null
        }
    }

    fun log(message: String) {
        try {
            target?.appendText(stamp.format(Date()) + "  " + message + "\n")
        } catch (t: Throwable) {
            // logging must never break a render
        }
    }

    fun log(tag: String, throwable: Throwable) {
        log(tag + ": " + throwable.javaClass.simpleName + ": " + throwable.message)
        try {
            target?.appendText(throwable.stackTraceToString() + "\n")
        } catch (t: Throwable) {
            // ignore
        }
    }

    fun file(context: Context): File = File(context.filesDir, "render_log.txt")
}
