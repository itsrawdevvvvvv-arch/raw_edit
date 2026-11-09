package com.rawedit.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.rawedit.app.data.Layer
import com.rawedit.app.data.Prefs
import com.rawedit.app.data.Style
import com.rawedit.app.prompt.PromptBuilder
import org.json.JSONArray
import org.json.JSONObject

/** Holds the whole create flow so every wizard step sees the same project. */
class CreateViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs.get(app)

    var audioUri: Uri? by mutableStateOf(null)
        private set
    var audioName: String by mutableStateOf("")
        private set
    var audioDurationMs: Long by mutableStateOf(0L)
        private set
    var script: String by mutableStateOf("")
    var style: Style? by mutableStateOf(null)
    var code: String by mutableStateOf("")
    var fastMode: Boolean by mutableStateOf(false)
    var layers: List<Layer> by mutableStateOf(emptyList())
    var layersJson: String? by mutableStateOf(null)
    var lastVideoPath: String? by mutableStateOf(null)
    var lastThumbPath: String? by mutableStateOf(null)
    var lastDurationMs: Long by mutableStateOf(0L)

    private var undoStack: List<List<Layer>> = emptyList()
    private var redoStack: List<List<Layer>> = emptyList()

    fun setAudio(uri: Uri, name: String, durationMs: Long) {
        audioUri = uri
        audioName = name
        audioDurationMs = durationMs
        saveDraft()
    }

    fun clearAudio() {
        audioUri = null
        audioName = ""
        audioDurationMs = 0L
    }

    fun canLeaveAudioStep(): Boolean = audioUri != null && audioDurationMs > 0

    fun canLeaveStyleStep(): Boolean = style != null

    fun prompt(): String = PromptBuilder.build(
        style = style ?: Style.MOTION,
        fileName = audioName.ifEmpty { "voiceover.mp3" },
        durationMs = audioDurationMs,
        script = script
    )

    // ---- layers ----

    fun setLayers(newLayers: List<Layer>, recordHistory: Boolean = true) {
        if (recordHistory) {
            undoStack = (undoStack + listOf(layers)).takeLast(30)
            redoStack = emptyList()
        }
        layers = newLayers
        layersJson = encodeLayers(newLayers)
    }

    /** Starts a gesture: snapshot once, then push live updates without new snapshots. */
    fun beginEdit() {
        undoStack = (undoStack + listOf(layers)).takeLast(30)
        redoStack = emptyList()
    }

    fun setLayersLive(newLayers: List<Layer>) {
        layers = newLayers
        layersJson = encodeLayers(newLayers)
    }

    fun updateLayer(id: String, block: (Layer) -> Layer) {
        layers = layers.map { if (it.id == id) block(it) else it }
        layersJson = encodeLayers(layers)
    }

    fun undo(): Boolean {
        val prev = undoStack.lastOrNull() ?: return false
        undoStack = undoStack.dropLast(1)
        redoStack = redoStack + listOf(layers)
        layers = prev
        layersJson = encodeLayers(prev)
        return true
    }

    fun redo(): Boolean {
        val next = redoStack.lastOrNull() ?: return false
        redoStack = redoStack.dropLast(1)
        undoStack = undoStack + listOf(layers)
        layers = next
        layersJson = encodeLayers(next)
        return true
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun encodeLayers(list: List<Layer>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    // ---- draft ----

    fun saveDraft() {
        try {
            val o = JSONObject()
            o.put("audioUri", audioUri?.toString() ?: "")
            o.put("audioName", audioName)
            o.put("audioDurationMs", audioDurationMs)
            o.put("script", script)
            o.put("style", style?.name ?: "")
            o.put("code", code)
            o.put("fastMode", fastMode)
            o.put("layers", layersJson ?: "")
            prefs.saveDraft(o.toString())
        } catch (t: Throwable) {
            // ignore
        }
    }

    fun loadDraft() {
        try {
            val raw = prefs.loadDraft() ?: return
            val o = JSONObject(raw)
            val uriString = o.optString("audioUri", "")
            if (uriString.isNotEmpty() && audioUri == null) {
                audioUri = Uri.parse(uriString)
                audioName = o.optString("audioName", "")
                audioDurationMs = o.optLong("audioDurationMs", 0L)
            }
            if (script.isEmpty()) script = o.optString("script", "")
            if (style == null) {
                val s = o.optString("style", "")
                style = Style.entries.firstOrNull { it.name == s }
            }
            if (code.isEmpty()) code = o.optString("code", "")
            fastMode = o.optBoolean("fastMode", false)
        } catch (t: Throwable) {
            // ignore
        }
    }

    fun startNewProject() {
        audioUri = null
        audioName = ""
        audioDurationMs = 0L
        script = ""
        style = null
        code = ""
        layers = emptyList()
        layersJson = null
        undoStack = emptyList()
        redoStack = emptyList()
        prefs.clearDraft()
    }
}
