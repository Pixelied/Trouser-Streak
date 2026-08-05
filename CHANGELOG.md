# Changelog

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
