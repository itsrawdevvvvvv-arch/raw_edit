package com.rawedit.app.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/** Small on-device settings store: profile name, profile photo, drafts. */
class Prefs(context: Context) {

    private val app = context.applicationContext
    private val sp = app.getSharedPreferences("raw_edit_prefs", Context.MODE_PRIVATE)

    var name: String
        get() = sp.getString("name", "Creator").orEmpty().ifEmpty { "Creator" }
        set(value) {
            sp.edit().putString("name", value.trim().ifEmpty { "Creator" }).apply()
        }

    var photoPath: String?
        get() = sp.getString("photo", null)
        set(value) {
            sp.edit().putString("photo", value).apply()
        }

    /** Copies the picked image into app storage so it survives permission loss. */
    fun saveProfilePhoto(uri: Uri): String? {
        return try {
            val resolver = app.contentResolver
            val dir = File(app.filesDir, "profile").apply { mkdirs() }
            val out = File(dir, "avatar.jpg")
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            } ?: return null
            photoPath = out.absolutePath
            out.absolutePath
        } catch (t: Throwable) {
            null
        }
    }

    fun removeProfilePhoto() {
        try {
            val p = photoPath
            if (p != null) File(p).delete()
        } catch (t: Throwable) {
            // ignore
        }
        photoPath = null
    }

    // ---- draft of the current project ----

    private fun draftFile(): File = File(app.filesDir, "drafts/draft.json")

    fun saveDraft(json: String) {
        try {
            val f = draftFile()
            f.parentFile?.mkdirs()
            f.writeText(json)
        } catch (t: Throwable) {
            // ignore
        }
    }

    fun loadDraft(): String? {
        return try {
            val f = draftFile()
            if (f.exists()) f.readText() else null
        } catch (t: Throwable) {
            null
        }
    }

    fun clearDraft() {
        try {
            draftFile().delete()
        } catch (t: Throwable) {
            // ignore
        }
    }

    fun clearCache(): Long {
        var freed = 0L
        try {
            val cache = app.cacheDir
            freed += cache.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            cache.deleteRecursively()
            cache.mkdirs()
        } catch (t: Throwable) {
            // ignore
        }
        try {
            val renders = File(app.filesDir, "renders/tmp")
            freed += renders.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            renders.deleteRecursively()
        } catch (t: Throwable) {
            // ignore
        }
        return freed
    }

    companion object {
        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs {
            return instance ?: synchronized(this) {
                instance ?: Prefs(context).also { instance = it }
            }
        }
    }
}
