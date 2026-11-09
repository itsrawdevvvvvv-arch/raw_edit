# Status report

Built by CI on GitHub Actions (run 42625854662, 2026-11-09). Debug and release APKs both
assembled successfully; `app-debug.apk` is 20.6 MB, v2-signed with the debug key.

No Android device or emulator was available in the build environment, so anything that needs a
running phone is marked **not verified**, not "pass".

## What the build proved

| Item | Result |
| --- | --- |
| Project configures and compiles (Kotlin 2.0.21, AGP 8.5.2, Compose) | Pass |
| Resources, manifest and adaptive icon merge | Pass |
| Debug APK assembles and is signed | Pass |
| Release APK assembles (signed with debug key, no release keystore given) | Pass |
| Fonts bundled in the APK (`assets/fonts/Montserrat-Bold.ttf`, `OpenSans-Regular.ttf`) | Pass |
| Package `com.rawedit.app`, versionName 1.0.0, minSdk 24, targetSdk 34 | Pass |
| No `INTERNET` permission | Pass |

## Testing checklist from the brief

| Item | Result |
| --- | --- |
| Audio picker works and shows name and duration | Not verified on a device (code review only) |
| Next is disabled until the audio and style are chosen | Not verified (logic reviewed) |
| Prompt contains style, file name and the RAW contract; Copy works | Prompt content reviewed; copy is not verified |
| Invalid code shows a friendly error; valid code starts rendering | Validator logic reviewed; not verified |
| Rendering shows real progress; Cancel works | Not verified |
| Output is a playable 1920x1080 MP4 with audio in sync | Not verified - this is the highest-risk path |
| Save to phone and Share work | Not verified |
| Library lists, plays and deletes videos | Not verified |
| Animations run smoothly; reduce-motion respected | Not verified |
| Layout fine on small and large phones | Not verified |

## Changes from the original brief

- **Rendering moved on-device.** The brief specified an online render server (Playwright +
  FFmpeg in Docker, hosted free). The app now renders on the phone: the WebView is driven frame
  by frame through `RAW.seek(t)`, frames go to MediaCodec (H.264), the voiceover is decoded and
  re-encoded to AAC, and MediaMuxer joins them. There is no `raw_edit_server` repository and no
  server URL, because nothing is uploaded anywhere.
- **No Room.** The video library is a small JSON file plus a `StateFlow`; settings and drafts use
  `SharedPreferences`. This keeps the build free of KSP/annotation processing.
- **Long-press on a library row** was replaced by explicit Share and Delete buttons, which are
  easier to hit on a phone.
- **Pinch-to-resize** is implemented as a two-finger gesture on the preview; there is also a size
  slider in the layer panel.
- `ACCESS_NETWORK_STATE` is merged into the manifest by an AndroidX library. It only reports
  connectivity state; it does not grant network access, and the app still declares no `INTERNET`
  permission.

## Known risk

The on-device renderer is the part most likely to need a fix on real hardware. Three specific
things could not be proven without a device:

1. Whether `WebView.draw(Canvas)` returns the freshly sought frame on every OEM WebView build
   (it depends on software-layer behaviour).
2. Whether the chosen hardware encoder accepts `COLOR_FormatYUV420Flexible` input images on every
   SoC; there is a fallback to NV12/I420 buffers if it does not.
3. Frame-accurate audio/video duration matching after muxing.

Speed: a 1 minute video at 1080p involves 1800 frames; expect several minutes on a mid-range
phone. The 720p "Fast mode" toggle roughly halves the encode work.
