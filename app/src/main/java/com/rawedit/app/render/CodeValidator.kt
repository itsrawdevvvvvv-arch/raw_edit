package com.rawedit.app.render

/** Static checks on the code pasted back from Claude. Never runs it here. */
object CodeValidator {

    const val MAX_BYTES = 5 * 1024 * 1024

    sealed class Result {
        data object Ok : Result()
        data class Bad(val message: String) : Result()
    }

    fun validate(code: String): Result {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            return Result.Bad("Paste the code Claude gave you first.")
        }
        if (trimmed.toByteArray().size > MAX_BYTES) {
            return Result.Bad("That code is too large. Ask Claude for a smaller single file.")
        }
        if (!trimmed.contains("<")) {
            return Result.Bad("That isn't an HTML file. Ask Claude to try again.")
        }
        if (!trimmed.contains("<!doctype", ignoreCase = true) && !trimmed.contains("<html", ignoreCase = true)) {
            return Result.Bad("That is not a full HTML file. Paste everything Claude returned.")
        }
        val hasRaw = trimmed.contains("window.RAW") || trimmed.contains("RAW =") ||
            trimmed.contains("RAW=") || trimmed.contains("RAW =")
        if (!hasRaw) {
            return Result.Bad("That code doesn't look right: no window.RAW found. Ask Claude to try again.")
        }
        if (!trimmed.contains("seek")) {
            return Result.Bad("The code has no seek(t) function. Ask Claude to add it.")
        }
        if (!trimmed.contains("layers")) {
            return Result.Bad("The code has no window.RAW.layers list. Ask Claude to add it.")
        }
        return Result.Ok
    }

    /** Wraps a bare code reply (no html tag) so the WebView still renders it. */
    fun normalize(code: String): String {
        val trimmed = code.trim()
        if (trimmed.startsWith("<")) return trimmed
        val start = trimmed.indexOf('<')
        return if (start > 0) trimmed.substring(start) else trimmed
    }
}
