package com.rawedit.app.net

import java.util.UUID

/**
 * The one place the render server address lives.
 *
 * After deploying raw_edit_server on Oracle Cloud Always Free, put the address here,
 * for example "http://203.0.113.10:7860". Users can also change it in About > Server.
 */
object ServerConfig {
    const val BASE_URL = "http://140.245.0.0:7860"

    fun normalize(url: String): String {
        var u = url.trim()
        if (u.isEmpty()) return BASE_URL
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
        return u.trimEnd('/')
    }

    fun isPlaceholder(url: String): Boolean = url.contains("140.245.0.0")

    fun newDeviceId(): String = UUID.randomUUID().toString()
}
