# HyperShot Implementation Plan

1. Scaffold Minecraft 26.2 / Fabric Loader 0.19.3 / Fabric API 0.156.0+26.2 / Loom 1.17.17 / Java 25 / Gradle 9.5.1.
2. Test-first dependency-free domain layer: checked arithmetic, resolution parsing, tile layout/projection, memory/disk budgets, filenames, recovery manifests, gallery filtering, scoped restoration.
3. Versioned atomic config and presets.
4. Client entrypoint, key mappings, native capture, HUD-only capture scope, cancellation.
5. Session queue, real progress/ETA, temporary files, metadata, index, thumbnails.
6. Blaze3D off-screen and tiled render adapter with overlap-aware off-axis projections and streaming PNG.
7. Quick panel, notification stack, gallery, viewer, settings, diagnostics.
8. Static source guards, unit tests, Gradle build, source archive, verification report.
