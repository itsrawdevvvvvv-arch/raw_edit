# raw_edit 1.1.0 - fix pass

## Root cause of "rendering never completes"

Two defects compounded, and together they produced exactly what the recording shows: a black
preview, progress frozen at 2%, then a silent return to the form.

1. **`evaluateJavascript` was awaited with no timeout.** `WebStage.load()` called a helper that
   suspended on `suspendCancellableCoroutine` and waited for the WebView callback. If that
   callback never fired - page not painted yet, WebView not laid out, renderer not ready - the
   coroutine suspended forever. The 20 second deadline was only checked *between* calls, so it
   was never reached. Progress therefore never left the initial 2%.

2. **Cancellation was treated as "user cancelled".** The render effect caught
   `CancellationException` and called `onCancel()`, which popped the screen. The effect was keyed
   on the WebView host, so any restart of that effect cancelled the running job and silently
   navigated back to the form - no error, no video, nothing in My videos.

Both are fixed. Every JS call now has a hard timeout (`withTimeoutOrNull`), every stage is wrapped
in try/catch with a specific message, and the screen only leaves on an explicit user cancel.

## What changed

Rendering:
- `WebStage` rewritten: timeouts on load, probe, seek, layer read and layer write; a structured
  `probe()` reporting `window.RAW`, `seek`, `layers`, `#stage` and duration.
- `RenderEngine` rewritten: real stages (Reading code, Preparing voice, Rendering frame N of M,
  Encoding, Saving poster, Muxing audio, Done), progress derived from frames completed, a
  resolution fallback chain 1080p to 720p to 480p, `OutOfMemoryError` caught and retried lower,
  even frame dimensions, and a friendly `RenderException` per failure.
- `VideoEncoder`: retries with the Baseline H.264 profile when the default profile is rejected.
- `RenderService`: a foreground service with a progress notification, so Android does not kill the
  render when the app is backgrounded or the screen is recorded.
- Live preview: the WebView stays laid out and visible, and the current frame is drawn over it.
- Cancel cleans up temp files; Retry and Back to code are offered on every failure.

Validation (before rendering, on the paste step): checks for an HTML document, `window.RAW`,
`seek(t)` and a `layers` list, with an inline message under the box.

UI:
- Bottom nav no longer covers content: `NavBottomSpacer()` adds nav height + the system
  navigation-bar inset on every scrolling screen.
- Nav pill no longer clips: the pill background is painted with a shape instead of clipping its
  children, and the width animates with `animateContentSize`, so the label never truncates.
- Home: the profile card no longer carries a rotation transform.
- Step 4: fixed layout with an internally scrolling code box, `imePadding`, and `adjustResize`,
  so the keyboard cannot shift the page.
- Back gesture: `BackHandler` steps back through the wizard and exits from Home.

Motion:
- One system in `MotionSpec`: 100 ms press, 150 ms small, 220 ms screen, 300 ms overlay cap;
  decelerate on enter, accelerate on exit.
- Step flow: 24 dp horizontal slide plus fade, direction-aware.
- Tab switch: fade-through with a 0.98 scale, animating only opacity and transform.
- Render screen: fully opaque, blocks touches underneath, nav hidden.
- Progress updates throttled to 4 per second while rendering.

Low-end (Android 9, 2 GB):
- `minSdk` 28, R8 and resource shrinking on release.
- Fast mode defaults on and is forced on `isLowRamDevice` or under 3 GB RAM; 24 fps on low-end,
  30 fps otherwise; 720p and 480p fallbacks.
- Single reused capture bitmap, no per-frame allocation, bitmaps recycled.

## Files changed

```
app/build.gradle.kts
app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/res/drawable/ic_stat_render.xml          (new)
app/src/main/java/com/rawedit/app/MainActivity.kt
app/src/main/java/com/rawedit/app/service/RenderService.kt   (new)
app/src/main/java/com/rawedit/app/render/WebStage.kt
app/src/main/java/com/rawedit/app/render/RenderEngine.kt
app/src/main/java/com/rawedit/app/render/VideoEncoder.kt
app/src/main/java/com/rawedit/app/render/CodeValidator.kt
app/src/main/java/com/rawedit/app/ui/theme/MotionSpec.kt      (new)
app/src/main/java/com/rawedit/app/ui/components/Components.kt
app/src/main/java/com/rawedit/app/ui/screens/RenderScreen.kt
app/src/main/java/com/rawedit/app/ui/screens/CreateScreen.kt
app/src/main/java/com/rawedit/app/ui/screens/HomeScreen.kt
app/src/main/java/com/rawedit/app/ui/screens/EditorScreen.kt
app/src/main/java/com/rawedit/app/ui/nav/AppNav.kt
app/src/main/java/com/rawedit/app/viewmodel/CreateViewModel.kt
```

## Honest status

Compiled and packaged by CI. **Not run on a device or emulator** - none is available in this
environment, so the acceptance checks in the brief (render time on an API 28 2 GB emulator, jank
percentage, four API levels) could not be executed. What is proven is that the code compiles,
both APKs build, and the specific failure modes above are fixed at the source level.

Unverified and worth watching on a real phone:
1. Whether `WebView.draw(Canvas)` returns the freshly sought frame on every OEM WebView.
2. Whether the hardware encoder accepts `COLOR_FormatYUV420Flexible` (there is an NV12/I420
   fallback, and a Baseline-profile retry).
3. Audio/video duration matching after muxing.
