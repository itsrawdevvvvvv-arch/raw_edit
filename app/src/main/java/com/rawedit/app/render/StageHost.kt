package com.rawedit.app.render

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.FrameLayout

/**
 * Hosts a WebView laid out at exactly 1920 x 1080 px and scales it down for display.
 * Frame capture always reads the full 1920 x 1080 layout, never the scaled preview.
 */
class StageHost(context: Context) : FrameLayout(context) {

    val web: WebView = WebView(context)

    init {
        setBackgroundColor(0xFF000000.toInt())
        clipChildren = true
        clipToPadding = true
        addView(web, LayoutParams(STAGE_W, STAGE_H))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        web.measure(
            MeasureSpec.makeMeasureSpec(STAGE_W, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(STAGE_H, MeasureSpec.EXACTLY)
        )
        applyScale()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyScale()
    }

    private fun applyScale() {
        if (measuredWidth == 0 || measuredHeight == 0) return
        val s = minOf(measuredWidth.toFloat() / STAGE_W, measuredHeight.toFloat() / STAGE_H)
        web.pivotX = 0f
        web.pivotY = 0f
        web.scaleX = s
        web.scaleY = s
        web.translationX = (measuredWidth - STAGE_W * s) / 2f
        web.translationY = (measuredHeight - STAGE_H * s) / 2f
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        web.layout(0, 0, STAGE_W, STAGE_H)
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
