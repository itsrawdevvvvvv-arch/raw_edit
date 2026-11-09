package com.rawedit.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File

/** The video library, stored as a small JSON file on the phone. No backend. */
class VideoStore(context: Context) {

    private val app = context.applicationContext
    private val file = File(app.filesDir, "library.json")

    private val _items = MutableStateFlow<List<VideoItem>>(emptyList())
    val items: StateFlow<List<VideoItem>> = _items.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        val list = mutableListOf<VideoItem>()
        try {
            if (file.exists()) {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val item = VideoItem.fromJson(arr.getJSONObject(i))
                    if (item.filePath.isNotEmpty() && File(item.filePath).exists()) list.add(item)
                }
            }
        } catch (t: Throwable) {
            // corrupted file: start clean
        }
        _items.value = list.sortedByDescending { it.createdAt }
        persist()
    }

    fun add(item: VideoItem) {
        _items.value = (listOf(item) + _items.value).sortedByDescending { it.createdAt }
        persist()
    }

    fun delete(id: String) {
        val target = _items.value.firstOrNull { it.id == id }
        _items.value = _items.value.filterNot { it.id == id }
        persist()
        target?.let {
            try {
                File(it.filePath).delete()
            } catch (t: Throwable) {
                // ignore
            }
            try {
                if (it.thumbPath.isNotEmpty()) File(it.thumbPath).delete()
            } catch (t: Throwable) {
                // ignore
            }
        }
    }

    fun count(): Int = _items.value.size

    private fun persist() {
        try {
            val arr = JSONArray()
            _items.value.forEach { arr.put(it.toJson()) }
            file.writeText(arr.toString())
        } catch (t: Throwable) {
            // ignore
        }
    }

    companion object {
        @Volatile
        private var instance: VideoStore? = null

        fun get(context: Context): VideoStore {
            return instance ?: synchronized(this) {
                instance ?: VideoStore(context).also { instance = it }
            }
        }
    }
}
