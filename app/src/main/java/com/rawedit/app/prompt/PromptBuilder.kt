package com.rawedit.app.prompt

import com.rawedit.app.data.Style
import java.util.Locale

/**
 * Builds the ready-to-paste prompt for Claude.
 * Plain text only, no emojis, no markdown fences around the reply request.
 */
object PromptBuilder {

    fun build(
        style: Style,
        fileName: String,
        durationMs: Long,
        script: String?
    ): String {
        val seconds = String.format(Locale.US, "%.1f", durationMs / 1000.0)
        val styleBlock = when (style) {
            Style.MOTION ->
                "STYLE: MOTION GRAPHICS. Bold geometric shapes, simple icons drawn with SVG or canvas, " +
                    "kinetic typography, smooth and varied transitions (wipes, masks, scale pushes, line reveals). " +
                    "Keep a strong accent of white on dark or a bold flat colour scheme. Nothing may sit still."
            Style.DOCUMENTARY ->
                "STYLE: DOCUMENTARY. Cinematic scene composition, slow zooms and pans, layered depth, " +
                    "lower-third captions, calm colour grading, gentle film grain or vignette done with CSS gradients. " +
                    "Each scene should feel like a framed shot, not a slide."
            Style.TYPOGRAPHY ->
                "STYLE: TYPOGRAPHY. Large animated words timed to the speech, very strong contrast, " +
                    "creative text motion (slide, mask reveal, letter stagger, scale punch), minimal decoration. " +
                    "The words are the whole design."
        }

        val scriptBlock = if (script.isNullOrBlank()) {
            "SCRIPT: not provided. Infer the content from the voiceover length and the style, and keep every " +
                "on-screen word generic but meaningful."
        } else {
            "SCRIPT / TOPIC (the words actually spoken in the voiceover):\n" + script.trim()
        }

        return """
You are writing the visual for a voiceover video. Reply with ONE single self-contained HTML file and nothing else.
No explanation before or after, no markdown fences, no commentary. Start with <!DOCTYPE html>.

VIDEO FACTS
- Voiceover file: $fileName
- Exact length: $seconds seconds
- Canvas: 1920 x 1080 (16:9), 30 fps
- $styleBlock
- $scriptBlock

HARD RULES
1. One single HTML file. All CSS, JS, SVG and canvas inline. No external files, no CDN links, no remote fonts,
   no remote images, no network requests of any kind. The file must work with the network switched off.
2. Design everything for a 1920 x 1080 stage. The page background is the stage; nothing scrolls.
3. Expose exactly this global contract:
   window.RAW = {
     duration: $seconds,
     width: 1920,
     height: 1080,
     layers: [ ... ],
     seek: function (t) { /* draw the exact frame at t seconds */ }
   }
   seek(t) must be fully deterministic: the same t always produces the same frame.
   Do not use setInterval, setTimeout, requestAnimationFrame, CSS animation, CSS transitions, video or audio
   elements for the animation. Every visual state must be computed from t inside seek(t) and written to the DOM.
4. Every frame must have something on screen. Fast pacing, a visual change on every beat, and a transition
   between every scene. No empty frames, no long static holds.
5. On-screen text only in English or Roman Hinglish. Never use Devanagari or any other script.
   Never show text that is not related to the voiceover.
6. Fonts: the two families "Montserrat" (bold, for headings) and "Open Sans" (body) are already available
   inside the app as local fonts. Use font-family: Montserrat, sans-serif; and font-family: 'Open Sans', sans-serif;
   Do not add @font-face, @import or Google Fonts links.
7. Compatibility: this file is rendered by an Android WebView in software mode. Do not use WebGL, backdrop-filter,
   filter: blur(), 3D transforms (rotate3d, translateZ, perspective), position: fixed, or CSS variables inside
   SVG attributes. Plain 2D CSS transforms, opacity, gradients, SVG and canvas 2D are all fine.
8. Keep total size under 4 MB.

LAYER CONTRACT (the app shows a small editor over your scene)
- window.RAW.layers is an array of every visible element, in back-to-front order, each shaped like:
  { id: "unique-id", name: "Short name", type: "text" | "shape" | "image",
    x: 0, y: 0, w: 400, h: 120, opacity: 1, z: 0, start: 0, end: 12 }
  x, y, w, h are in stage pixels (1920 x 1080). start and end are seconds. z is the stacking order.
- seek(t) must read window.RAW.layers on every call and draw each element from those values, so that when the
  app changes x, y, w, h, opacity, z, start or end the very next frame shows the change. Any animation you design
  runs relative to each layer's base values, and must still be computed from t.
- An element whose current time is outside its start..end window must not be drawn.
- If a layer has type "image" and a non-empty src (a data URL added by the user), draw it as an image at its rect
  with its opacity, inside its time window, in its z position. Support these user-added images out of the box.
- Elements that are not in layers will not be editable. Keep the layer list complete.

QUALITY BAR
- A viewer should never see a frame that looks broken, empty or half laid out.
- Vary the animation types; do not use the same bounce everywhere.
- Make the first 2 seconds strong, and end on a clean final frame that holds to the last second.

Return the HTML file only.
""".trim()
    }
}
