package com.rawedit.app.render

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Choreographer
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Loads the pasted HTML into a locked-down WebView and drives RAW.seek(t).
 * All network is blocked: every request is either answered from the APK assets
 * or replaced with an empty response.
 */
class WebStage(private val context: Context, private val web: WebView) {

    private var pageReady = false

    @SuppressLint("SetJavaScriptEnabled")
    fun prepare() {
        web.setBackgroundColor(0xFF000000.toInt())
        web.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        val s = web.settings
        s.javaScriptEnabled = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.domStorageEnabled = false
        s.databaseEnabled = false
        s.setGeolocationEnabled(false)
        s.blockNetworkLoads = true
        s.blockNetworkImage = true
        s.mediaPlaybackRequiresUserGesture = true
        s.javaScriptCanOpenWindowsAutomatically = false
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString().orEmpty()
                return serve(url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = true
        }
    }

    private fun serve(url: String): WebResourceResponse {
        if (url.startsWith(ASSET_BASE)) {
            val path = url.removePrefix(ASSET_BASE)
            if (path.startsWith("fonts/")) {
                return try {
                    val bytes = context.assets.open(path).readBytes()
                    WebResourceResponse("font/ttf", null, ByteArrayInputStream(bytes))
                } catch (t: Throwable) {
                    emptyResponse()
                }
            }
        }
        return emptyResponse()
    }

    private fun emptyResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    /** Loads the code and waits until window.RAW.seek is available. */
    suspend fun load(code: String): Boolean = withContext(Dispatchers.Main) {
        pageReady = false
        val html = buildPage(code)
        web.loadDataWithBaseURL(ASSET_BASE, html, "text/html", "utf-8", null)

        val deadline = System.currentTimeMillis() + 20_000
        var attempts = 0
        while (System.currentTimeMillis() < deadline) {
            attempts++
            val probe = evalJs(
                "(function(){try{return (window.RAW && typeof window.RAW.seek==='function')?'1':'0';}catch(e){return '0';}})()"
            )
            if (probe.contains("1")) {
                pageReady = true
                break
            }
            awaitFrame()
            if (attempts > 4) kotlinx.coroutines.delay(40)
        }
        pageReady
    }

    /** Injects the font faces plus a small helper for layer edits. */
    private fun buildPage(code: String): String {
        val head = """
            <style>
              @font-face { font-family: 'Montserrat'; font-weight: 700; font-style: normal;
                src: url('$ASSET_BASE fonts/Montserrat-Bold.ttf') format('truetype'); }
              @font-face { font-family: 'Open Sans'; font-weight: 400; font-style: normal;
                src: url('$ASSET_BASE fonts/OpenSans-Regular.ttf') format('truetype'); }
              html, body { margin: 0; padding: 0; background: #000; overflow: hidden; }
            </style>
        """.trimIndent().replace("$ASSET_BASE ", ASSET_BASE)

        return if (code.contains("</head>", ignoreCase = true)) {
            code.replaceFirst(Regex("</head>", RegexOption.IGNORE_CASE), "$head</head>")
        } else {
            "<!DOCTYPE html><html><head>$head</head><body>$code</body></html>"
        }
    }

    suspend fun seek(t: Double): Boolean = withContext(Dispatchers.Main) {
        if (!pageReady) return@withContext false
        val ok = evalJs(
            "(function(){try{window.RAW.seek($t);return '1';}catch(e){return '0';}})()"
        )
        web.invalidate()
        awaitFrame()
        ok.contains("1")
    }

    suspend fun readLayers(): String = withContext(Dispatchers.Main) {
        evalJs(
            "(function(){try{return JSON.stringify((window.RAW&&window.RAW.layers)||[]);}catch(e){return '[]';}})()"
        )
    }

    suspend fun writeLayers(json: String): Boolean = withContext(Dispatchers.Main) {
        val escaped = jsString(json)
        val ok = evalJs(
            "(function(){try{window.RAW.layers = JSON.parse($escaped);" +
                " if(window.__rawHook){window.__rawHook();} return '1';}catch(e){return '0';}})()"
        )
        web.invalidate()
        awaitFrame()
        ok.contains("1")
    }

    suspend fun durationSeconds(): Double = withContext(Dispatchers.Main) {
        val v = evalJs("(function(){try{return String(window.RAW.duration||0);}catch(e){return '0';}})()")
        v.trim().trim('"').toDoubleOrNull() ?: 0.0
    }

    fun drawTo(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        web.draw(canvas)
    }

    private suspend fun evalJs(js: String): String = suspendCancellableCoroutine { cont ->
        try {
            web.evaluateJavascript(js) { value ->
                if (cont.isActive) cont.resume(value ?: "")
            }
        } catch (t: Throwable) {
            if (cont.isActive) cont.resume("")
        }
    }

    private suspend fun awaitFrame() = suspendCancellableCoroutine<Unit> { cont ->
        Choreographer.getInstance().postFrameCallback {
            if (cont.isActive) cont.resume(Unit)
        }
    }

    /** JSON.stringify gives a JS-safe literal, but we quote it once more for the JS string. */
    private fun jsString(json: String): String {
        val out = StringBuilder("\"")
        for (ch in json) {
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (ch.code < 32) out.append(' ') else out.append(ch)
            }
        }
        out.append("\"")
        return out.toString()
    }

    fun destroy() {
        try {
            web.loadUrl("about:blank")
            web.destroy()
        } catch (t: Throwable) {
            // ignore
        }
    }

    companion object {
        const val ASSET_BASE = "https://rawedit.local/"
        fun blank(): ByteArrayOutputStream = ByteArrayOutputStream()
        fun uriOf(s: String): Uri = Uri.parse(s)
    }
}
