# No Limits Local

No Limits Local is an Android-only, offline photo enhancement app. It does not use a cloud backend and the Android manifest intentionally has **no INTERNET permission**.

## What this build does locally

- Selective shadow recovery while protecting highlights
- Automatic tonal analysis and gentle percentile normalization
- Warmth/cooling and color-depth adjustment
- Edge-aware noise cleanup
- Local clarity and mild motion-softness/deblur enhancement
- Natural-looking contrast adjustment
- Prompt keyword interpretation performed locally on-device
- JPEG export up to a configurable 1080–2560 px long side

The design goal is to preserve information already present in the photo rather than generate replacement content. It can reveal detail hidden by darkness, improve perceived clarity, reduce mild noise/softness, and improve color/tonal balance. It cannot genuinely reconstruct information that was never captured.

## Privacy

The installed APK has no Android Internet permission. Images are read from the picker, processed in memory on the phone, and only written to `Pictures/No Limits` when the user taps Save Result.

## Android build

The Android project is in `android/`. GitHub Actions builds a debug APK on pushes to `main`.

## Upstream source

The original InstructPix2Pix repository remains in `original-instruct-pix2pix/` for reference and retains its original license. It is not executed by the local Android enhancement path in this build.
