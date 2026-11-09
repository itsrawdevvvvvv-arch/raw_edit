package com.rawedit.app.render

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

/**
 * Loads the pasted HTML into a locked-down WebView and drives RAW.seek(t).
 *
 * Every JS call has a hard timeout: an evaluateJavascript callback that never fires
 * used to hang the render forever, which is what made progress freeze at 2%.
 */
class WebStage(private val context: Context, private val web: WebView) {

    private var pageReady = false

    data class Probe(
        val hasRaw: Boolean = false,
        val hasSeek: Boolean = false,
        val hasLayers: Boolean = false,
        val hasStage: Boolean = false,
        val duration: Double = 0.0
    )

    @SuppressLint("SetJavaScriptEnabled")
    fun prepare() {
        web.setBackgroundColor(0xFF000000.toInt())
        web.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        val s = web.settings
        s.javaScriptEnabled = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
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
            ): WebResourceResponse? = serve(request?.url?.toString().orEmpty())

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
                    WebResourceResponse(
                        "font/ttf", null,
                        ByteArrayInputStream(context.assets.open(path).readBytes())
                    )
                } catch (t: Throwable) {
                    emptyResponse()
                }
            }
        }
        return emptyResponse()
    }

    private fun emptyResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    /** Loads the code and waits until window.RAW.seek is available, or gives up. */
    suspend fun load(code: String): Boolean = withContext(Dispatchers.Main) {
        pageReady = false
        try {
            web.loadDataWithBaseURL(ASSET_BASE, buildPage(code), "text/html", "utf-8", null)
        } catch (t: Throwable) {
            return@withContext false
        }
        val deadline = SystemClock.uptimeMillis() + LOAD_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (probeOnce().hasSeek) {
                pageReady = true
                return@withContext true
            }
            delay(80)
        }
        false
    }

    suspend fun probe(): Probe = withContext(Dispatchers.Main) { probeOnce() }

    private suspend fun probeOnce(): Probe {
        val raw = evalJs(JS_PROBE, 2500) ?: return Probe()
        return try {
            val o = JSONObject(unquote(raw))
            Probe(
                hasRaw = o.optInt("r", 0) == 1,
                hasSeek = o.optInt("s", 0) == 1,
                hasLayers = o.optInt("l", 0) == 1,
                hasStage = o.optInt("st", 0) == 1,
                duration = o.optDouble("d", 0.0)
            )
        } catch (t: Throwable) {
            Probe()
        }
    }

    suspend fun seek(t: Double): Boolean = withContext(Dispatchers.Main) {
        if (!pageReady) return@withContext false
        val ok = evalJs("(function(){try{window.RAW.seek($t);return '1';}catch(e){return '0';}})()", 4000)
        web.invalidate()
        awaitFrame()
        ok != null && ok.contains("1")
    }

    suspend fun readLayers(): String = withContext(Dispatchers.Main) {
        evalJs(
            "(function(){try{return JSON.stringify((window.RAW&&window.RAW.layers)||[]);}catch(e){return '[]';}})()",
            3000
        ) ?: "[]"
    }

    suspend fun writeLayers(json: String): Boolean = withContext(Dispatchers.Main) {
        val ok = evalJs(
            "(function(){try{window.RAW.layers = JSON.parse(${jsString(json)});" +
                "if(window.__rawHook){window.__rawHook();}return '1';}catch(e){return '0';}})()",
            3000
        )
        web.invalidate()
        awaitFrame()
        ok != null && ok.contains("1")
    }

    private fun buildPage(code: String): String {
        val head = "<style>" +
            "@font-face{font-family:'Montserrat';font-weight:700;font-style:normal;" +
            "src:url('" + ASSET_BASE + "fonts/Montserrat-Bold.ttf') format('truetype');}" +
            "@font-face{font-family:'Open Sans';font-weight:400;font-style:normal;" +
            "src:url('" + ASSET_BASE + "fonts/OpenSans-Regular.ttf') format('truetype');}" +
            "html,body{margin:0;padding:0;background:#000;overflow:hidden;}" +
            "</style>"
        return if (code.contains("</head>", ignoreCase = true)) {
            code.replaceFirst(Regex("</head>", RegexOption.IGNORE_CASE), "$head</head>")
        } else {
            "<!DOCTYPE html><html><head>$head</head><body>$code</body></html>"
        }
    }

    fun drawTo(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF000000.toInt())
        web.draw(canvas)
    }

    /** Never hangs: returns null if the WebView does not answer in time. */
    private suspend fun evalJs(js: String, timeoutMs: Long): String? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<String?> { cont ->
                try {
                    web.evaluateJavascript(js) { value ->
                        if (cont.isActive) cont.resume(value)
                    }
                } catch (t: Throwable) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }

    private suspend fun awaitFrame() = withTimeoutOrNull(120) {
        suspendCancellableCoroutine<Unit> { cont ->
            Choreographer.getInstance().postFrameCallback {
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    private fun unquote(value: String): String {
        var v = value.trim()
        if (v.startsWith("\"") && v.endsWith("\"") && v.length >= 2) {
            v = v.substring(1, v.length - 1)
        }
        return v.replace("\\\"", "\"").replace("\\\\", "\\")
    }

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
        pageReady = false
        try {
            web.loadUrl("about:blank")
            web.destroy()
        } catch (t: Throwable) {
            // ignore
        }
    }

    companion object {
        const val ASSET_BASE = "https://rawedit.local/"
        private const val LOAD_TIMEOUT_MS = 15_000L
        private const val JS_PROBE =
            "(function(){try{var r=window.RAW;return JSON.stringify({" +
                "r:r?1:0,s:(r&&typeof r.seek==='function')?1:0," +
                "l:(r&&r.layers&&r.layers.length)?1:0,d:(r&&r.duration)||0," +
                "st:document.getElementById('stage')?1:0});}catch(e){return '{}';}})()"
    }
}
