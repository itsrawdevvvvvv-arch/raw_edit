package com.rawedit.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * One motion system for the whole app.
 * Nothing longer than 300 ms, ever.
 */
object MotionSpec {
    const val PRESS = 100
    const val SMALL = 150
    const val SCREEN = 220
    const val OVERLAY = 300

    /** Decelerate: for anything entering. */
    val Enter: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Accelerate: for anything leaving. */
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
}
