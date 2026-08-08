# Changelog

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
