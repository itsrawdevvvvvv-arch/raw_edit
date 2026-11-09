# raw_edit

Turn an MP3 voiceover into a 1920x1080 video, entirely on the phone.

You add a voice file, pick a style, the app writes a ready-made prompt for Claude, you paste
Claude's single HTML file back into the app, and the app renders the MP4 on the device.

No account, no server, no ads, no payments, no network permission at all.

## How it works

1. **Add your voice** - pick an MP3 (or any audio the phone can read). The app shows the file
   name and duration. An optional script box makes the generated video match what is spoken,
   because Claude cannot hear the audio.
2. **Pick a style** - Motion Graphics, Documentary or Typography.
3. **Copy your prompt** - the app builds a prompt containing the file name, the exact duration,
   the style rules and the `window.RAW` contract. Paste it into Claude.
4. **Have a code?** - paste the HTML Claude returned. It is validated before it is accepted.
5. **Edit video** (optional) - a small layer editor: live preview, move and scale layers,
   opacity, add an image, per-layer timing, reorder, undo and redo.
6. **Create video** - the phone drives `RAW.seek(t)` frame by frame in a locked-down WebView,
   encodes H.264 with MediaCodec, re-encodes the voiceover to AAC and muxes both with
   MediaMuxer. Result: `Movies/raw_edit` via MediaStore, or share it straight away.

## The contract the app asks Claude for

```js
window.RAW = {
  duration: 62.4,
  width: 1920,
  height: 1080,
  layers: [ { id, name, type, x, y, w, h, opacity, z, start, end, src } ],
  seek: function (t) { /* draw the exact frame at t seconds, deterministically */ }
}
```

`seek(t)` must be deterministic and read `window.RAW.layers` every frame, so editor changes
show up immediately and are rendered exactly as previewed.

## Security

- The app declares **no INTERNET permission**.
- The WebView has `blockNetworkLoads = true`, no file access, no content access, no JS bridge.
  Every request is either answered from APK assets (the two bundled fonts) or replaced with an
  empty response.
- Pasted code is checked before it is loaded: non-empty, under 5 MB, contains `window.RAW` and a
  `seek` function, and is HTML.

## Project layout

```
app/src/main/java/com/rawedit/app/
  data/      models, JSON video library, preferences and drafts
  prompt/    the prompt builder
  render/    WebView stage, code validation, H.264 encoder, AAC transcoder, muxer, engine
  ui/        theme, components, screens, navigation
  util/      audio metadata, MediaStore saving, formatters, motion setting
```

## Build

Requirements: JDK 17, Android SDK 34.

```bash
gradle :app:assembleDebug
# or
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

CI: `.github/workflows/build.yml` builds debug and release APKs on every push and uploads them
as artifacts. The release build is signed with the debug keystore because no release keystore
was provided.

## Honest status

See `STATUS.md` for what was verified by a real build and what could only be verified by code
review (no physical device was available in the build environment).
