# Changelog

## 0.2.1-rc2 — 2026-08-08

- Added the hybrid HyperShot camera workflow: tap F2 for a fast Photo, hold F2 to enter the in-world viewfinder, and use F7 for the cursor-enabled camera control drawer while preserving normal mouselook during composition.
- Added first-class Photo, Burst, Time, and Cinematic camera modes without duplicating the renderer: higher-level shot orchestration still delegates one rendered/encoded image at a time to the existing `CaptureManager`.
- Added configurable Timer, composition guides, guide opacity, Shader Settle profiles, readable shot-readiness states, and idle viewfinder-chrome fading. Camera/viewfinder overlays remain suppressed during the actual capture pass.
- Added Burst capture with configurable frame count/cadence, whole-session preflight math, sequential bounded-memory capture, capture-group metadata, and grouped Gallery/contact-sheet support.
- Added Time-of-Day Bracketing for singleplayer using Minecraft 26.2's native `WorldClock`/`ServerClockManager` APIs. HyperShot snapshots the exact original clock value, applies each requested lighting state, optionally settles shaders between frames, captures sequentially, and restores the original clock afterward.
- Added Cinematic mode with capability-gated camera/FOV lock, Clean Frame, nearby-chunk readiness, configurable chunk timeout, optional integrated-server time/weather control, and optional vanilla world tick freeze. Server-owned time/weather/freeze controls are disabled instead of faked on multiplayer.
- Added explicit `Capture anyway` / `Cancel shot` actions when Cinematic nearby-chunk readiness times out; HyperShot never silently takes the frame after a timeout.
- Added a single restoration path for Cinematic success, cancellation, and failure. Restoration is reusable across multiple shots and handles cancellation while the asynchronous server snapshot is still pending.
- Added dedicated Camera Behavior pages for Photography, Sequences, Cinematic, and F2/Viewfinder interaction. Cinematic includes a one-click restore to conservative recommended defaults; world freeze remains off by default pending real-hardware validation.
- Migrated camera configuration to schema 6 while preserving existing capture presets and earlier camera/sequence settings.
- Fixed a Time-mode regression where post-lighting Shader Settle could loop back into applying the same time state instead of starting the frame capture.
- Fixed Java 25/Minecraft 26.2 integration issues caught during development, including private `ChunkPos` coordinate fields and complete readiness-state handling in the viewfinder.
- Expanded dependency-free camera/core coverage to 212 assertions, including Time continuation, Cinematic capability gating, orchestration order, pre-snapshot cancellation, and second-session restoration. Java 25 Gradle build, packaged-JAR inspection, and Minecraft 26.2 startup smoke are green on the full Photo/Burst/Time/Cinematic stack.
- This remains a hardware-test release candidate. Automated startup does not execute the real rendered screenshot pass; Native/4K/8K Photo plus Burst/Time/Cinematic captures and restoration still need validation on the target installation before final release.
- 32K UHD remains experimental/high-load until a complete 30,720×17,280 image is produced and verified. Cinematic world freeze also remains disabled by default until a real in-world freeze/capture/restore test is completed.

## 0.2.1-rc1 — 2026-08-08

- Removed the Java 25-incompatible write to `GameRenderer.mainRenderTarget` that caused captures to crash with `IllegalAccessError` on real Minecraft 26.2 installations.
- Added a capture-scoped render-target context and a narrow `GameRenderer.mainRenderTarget()` interception so Minecraft's final render-target field remains untouched.
- Added a source/JAR regression guard that rejects the old `hypershot$setMainRenderTarget` path.
- Fixed HyperShot screen draw order so custom panels render behind Minecraft buttons and edit boxes instead of darkening/covering active controls.
- Rebuilt Settings around a simpler default flow with Native, 4K, 8K, 16K, 32K, and Custom resolution choices.
- Defined 32K UHD as 30,720×17,280 and marked it experimental/high-load until a complete real-hardware 32K capture is verified.
- Added Simple and Advanced settings modes. Advanced mode contains exact dimensions, tile size/overlap, PNG save effort, JPEG quality, metadata privacy, disk reserve, diagnostics, and recommended-setting reset controls.
- Replaced unexplained PNG compression numbers in the main UI with Fast save, Balanced, and Smallest file wording; all are explicitly lossless and the same visual quality.
- Labeled JPEG as lossy and explained that lower quality permanently discards detail.
- Added capture preflight estimates for tile count, temporary storage, estimated final size, available resources, and safety states: Safe, Likely safe, High load, Not recommended, and Cannot start.
- Expanded the dependency-free regression suite to 93 assertions covering UHD presets, extreme-size estimates, safety behavior, and responsive UI layout.
- This is a hardware-test release candidate, not the final 0.2.1 release. A real Native/4K/8K capture on the originally failing Java 25/macOS environment is still required before promotion.

## 0.2.0-beta.1 — 2026-08-05

- Added first-class Mod Menu 20.0.1 configuration-screen support for Minecraft 26.2.
- Rebuilt every HyperShot screen around one responsive Minecraft-native visual system.
- Added sidebar settings on large screens and paginated compact settings on small GUI scales.
- Added overlap-proof footer layouts and compact gallery actions.
- Improved quick-capture workload summaries, image preview framing, and diagnostics presentation.
- Refined stacked screenshot cards with a real preview, clearer capture progress, status colors, hover behavior, and click/right-click actions.
- Added layout regression tests for wide, compact, and densely packed control rows.
- Preserved the verified bounded-memory tiled capture pipeline, 10,000×10,000 synthetic test, and client startup smoke test.

## 0.1.0-alpha.1 — 2026-08-04

- Initial Minecraft 26.2 Fabric project with pinned Java 25, Loom 1.17.17, Loader 0.19.3, API 0.156.0+26.2, and Gradle 9.5.1.
- Added backend-independent off-screen and tiled renderer.
- Added checked tile projection, overlap cropping, disk-backed assembly, streaming PNG, bounded JPEG, atomic outputs, recovery, pause/cancel, and restoration.
- Added capture notification cards, quick panel, settings, diagnostics, indexed gallery, thumbnails, preview, reveal, favorites, soft delete, and undo.
- Added metadata privacy modes and dependency-free regression suite.
