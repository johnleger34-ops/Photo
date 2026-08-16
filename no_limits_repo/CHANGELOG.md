# Changelog

## v0.2.1-local
- Fixed Android build failure caused by Java 1.8 / Kotlin 17 JVM-target mismatch.
- Java and Kotlin now both compile for JVM 17.
- Added explicit Material 3 experimental API opt-in for `TopAppBar`.
- No photo-enhancement behavior was changed.

## 0.2.0-local
- Removed all cloud/server code and Android network access.
- Removed OkHttp and INTERNET permission.
- Added fully on-device adaptive enhancement engine.
- Added selective shadow recovery, highlight protection, tonal analysis, color depth, warmth, denoise, clarity, and mild motion-softness cleanup.
- Added local prompt keyword interpretation.
- Added configurable 1080–2560 px output long side.
- Photos never leave the device in this build.
