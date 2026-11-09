package com.rawedit.app.render

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * Hosts a WebView laid out at exactly stageW x stageH px and scales it down for display.
 * Frame capture always reads the full stage layout, never the scaled preview.
 *
 * The stage is created at the render resolution (720p in Fast mode), which removes a
 * full-size 1080p draw plus a downscale for every single frame.
 */
class StageHost(
    context: Context,
    val stageW: Int = STAGE_W,
    val stageH: Int = STAGE_H
) : FrameLayout(context) {

    val web: WebView = WebView(context)

    init {
        setBackgroundColor(0xFF000000.toInt())
        clipChildren = true
        clipToPadding = true
        addView(web, LayoutParams(stageW, stageH))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        web.measure(
            MeasureSpec.makeMeasureSpec(stageW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(stageH, MeasureSpec.EXACTLY)
        )
        applyScale()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyScale()
    }

    private fun applyScale() {
        if (measuredWidth == 0 || measuredHeight == 0) return
        val s = minOf(measuredWidth.toFloat() / stageW, measuredHeight.toFloat() / stageH)
        web.pivotX = 0f
        web.pivotY = 0f
        web.scaleX = s
        web.scaleY = s
        web.translationX = (measuredWidth - stageW * s) / 2f
        web.translationY = (measuredHeight - stageH * s) / 2f
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        web.layout(0, 0, stageW, stageH)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun lockInput() {
        web.isClickable = false
        web.isFocusable = false
        web.setOnTouchListener { _: View, _ -> true }
    }

    companion object {
        const val STAGE_W = 1920
        const val STAGE_H = 1080
    }
}
