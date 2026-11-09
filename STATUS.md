# raw_edit 1.2.0

## Why the update failed

Every CI run generated a fresh debug keystore, so each APK was signed with a different key.
Android refuses to update an app whose signing certificate changed.

A keystore is now committed at `keystore/raw_edit.jks` and both the debug and the release build
type sign with it (`signingConfigs { create("stable") }`). Verified: the debug and release APKs of
1.2.0 carry the same certificate (SHA-256 prefix 978711c060b59770), so every future build installs
as an update.

**One time only:** the versions installed so far (1.0.0, 1.1.0) were signed with the old
throwaway key. Uninstall the app once, then install 1.2.0. Every update after that installs
normally over the previous one.

## What the screen recording showed, and what changed

| Seen in the recording | Fix |
| --- | --- |
| Step 3 drew "Pick a style" and "Copy your prompt" on top of each other, prompt text bleeding through the style cards | The step transition used a cross-fade, so both steps were composed and drawn together. The outgoing step is now removed instantly (`ExitTransition.None`) and only the new step slides in |
| The white Next button sat underneath the floating nav pill on every step | Added `NavPillClearance()` (84 dp + gesture inset) to the wizard footer |
| The audio filename overflowed the dashed drop area and was cut at both edges | Name is now 2 lines maximum, centred, ellipsised, with horizontal padding |
| Home scrolled under the status bar and clipped "Creator!" | The device reports a zero status-bar inset under edge-to-edge. Every screen now applies `rememberTopInset()`, which falls back to 28 dp when the system reports less than 8 dp. The inset is applied before the scroll modifier so scrolled content clips at the boundary |
| The prompt box cut a line in half at its bottom edge | Trailing 14 dp inside the scrolling text |
| About screen text clipped at the very bottom | `navigationBarsPadding()` plus a larger trailing spacer |
| Lag across the app | See below |

## Lag work

- The hero dot grid drew about 1200 circles on every scroll frame. It is now one tiled
  `BitmapShader`, drawn once per size.
- The progress bar ran an infinite shimmer animation, which invalidated and redrew the bar every
  frame for the whole render. It is a plain fill now.
- Tab transitions animated a scale on the full screen, forcing an offscreen render of the entire
  hierarchy each frame. They are fade-only now (150 ms in, 90 ms out).
- The upload icon's float animation was read during composition, recomposing the whole step each
  frame. It now animates on the render layer (`graphicsLayer`) with no recomposition.
- The editor pushed every drag delta to the WebView. It debounces 60 ms now.
- The render stage is built at the output resolution (1280x720 in Fast mode) instead of always
  1920x1080 and downscaling, which removes a full-size WebView draw per frame.

## Render changes

- The stage is created at the render resolution, so the primary path needs no downscale.
- `seek(0)` is validated before the loop; a throwing `seek` now reports itself instead of
  producing a broken video.
- A blank-frame watchdog samples every 15th frame; if more than 90% are pure black the render
  fails with "Every frame came out black. This phone's WebView couldn't draw the code."
- `domStorageEnabled` and `databaseEnabled` are on (local only, no network) because some
  generated pages use localStorage and previously threw.
- `useWideViewPort` and `loadWithOverviewMode` are set explicitly.

## Still unverified

No device or emulator is available here, so the recording is the only evidence of behaviour and
these remain unproven:
1. Whether `WebView.draw(Canvas)` returns the freshly sought frame on this WebView build.
2. Whether the hardware encoder accepts `COLOR_FormatYUV420Flexible` (NV12/I420 fallback and a
   Baseline-profile retry exist).
3. Audio/video duration matching after muxing.

If a render fails now, the screen names the stage that failed. That message is what I need to
fix it properly.

## 1.3.0

Two failures reported from a real device, both fixed.

**"The code loaded but seek(0) failed."** The pasted HTML was valid. I ran it in headless
Chromium here: `window.RAW` present, `seek` is a function, 83 layers, `seek(0)` returns true in
7 ms, 30 seeks in 850 ms. The bug was mine: the first seek ran once behind a 4 second timeout and
any miss was fatal. The first seek also builds the whole DOM, so a slow phone can exceed it.
Now the warm-up runs up to three times, never blocks the render, and the frame loop plus the
blank-frame watchdog decide whether the page actually works. JS timeouts went from 4 s to 8 s.

**"Couldn't read the voice file."** The picked file was a WAV. The old transcoder only handled
compressed audio through a MediaCodec decoder, and raw PCM has no decoder on many devices.
`AudioTranscoder` is rewritten with four routes, tried in order:
1. track is already AAC - remux the samples, no re-encode;
2. track is raw PCM (WAV) - feed straight to the AAC encoder, no decoder;
3. anything else (MP3, M4A, OGG) - decode, then encode;
4. MediaExtractor refuses the file - a hand-written RIFF/WAVE parser that handles PCM 8/16/24/32
   bit and 32 bit float, mono or stereo, and encodes it itself.

The error message is now "Pick the audio again, or convert it to MP3" rather than implying the
format was wrong.
