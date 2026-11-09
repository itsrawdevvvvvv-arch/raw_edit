package com.rawedit.app.data

import org.json.JSONObject

/**
 * One editable layer of the generated scene.
 * Contract mirrored from the prompt given to Claude:
 * { id, name, type, x, y, w, h, opacity, z, start, end }
 */
data class Layer(
    val id: String,
    val name: String,
    val type: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val opacity: Float = 1f,
    val z: Int = 0,
    val start: Float = 0f,
    val end: Float = 0f,
    val src: String? = null,
    val hidden: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("w", w.toDouble())
        put("h", h.toDouble())
        put("opacity", (if (hidden) 0.0 else opacity.toDouble()))
        put("z", z)
        put("start", start.toDouble())
        put("end", end.toDouble())
        if (src != null) put("src", src)
    }

    companion object {
        fun fromJson(o: JSONObject): Layer = Layer(
            id = o.optString("id", "layer_" + System.nanoTime()),
            name = o.optString("name", "Layer"),
            type = o.optString("type", "shape"),
            x = o.optDouble("x", 0.0).toFloat(),
            y = o.optDouble("y", 0.0).toFloat(),
            w = o.optDouble("w", 100.0).toFloat(),
            h = o.optDouble("h", 100.0).toFloat(),
            opacity = o.optDouble("opacity", 1.0).toFloat(),
            z = o.optInt("z", 0),
            start = o.optDouble("start", 0.0).toFloat(),
            end = o.optDouble("end", 0.0).toFloat(),
            src = if (o.has("src") && !o.isNull("src")) o.optString("src") else null,
            hidden = false
        )
    }
}

data class VideoItem(
    val id: String,
    val title: String,
    val style: String,
    val durationMs: Long,
    val filePath: String,
    val thumbPath: String,
    val savedUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("style", style)
        put("durationMs", durationMs)
        put("filePath", filePath)
        put("thumbPath", thumbPath)
        put("savedUri", savedUri ?: "")
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): VideoItem = VideoItem(
            id = o.optString("id", ""),
            title = o.optString("title", "Untitled"),
            style = o.optString("style", "Motion Graphics"),
            durationMs = o.optLong("durationMs", 0L),
            filePath = o.optString("filePath", ""),
            thumbPath = o.optString("thumbPath", ""),
            savedUri = o.optString("savedUri", "").ifEmpty { null },
            createdAt = o.optLong("createdAt", 0L)
        )
    }
}

enum class Style(val label: String, val blurb: String) {
    MOTION("Motion Graphics", "Bold shapes and kinetic text"),
    DOCUMENTARY("Documentary", "Cinematic scenes, slow moves"),
    TYPOGRAPHY("Typography", "Big words timed to the voice")
}
