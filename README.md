# HyperShot

HyperShot is a client-only Fabric screenshot system for Minecraft Java Edition 26.2. The current beta implements real off-screen and tiled scene rendering through Minecraft 26.2's Blaze3D abstractions, disk-backed assembly, streaming PNG output, bounded JPEG output, guaranteed render-state restoration, live progress, clickable thumbnail cards, metadata, diagnostics, and an indexed in-game gallery.

## Requirements

- Minecraft Java Edition 26.2
- Fabric Loader 0.19.3
- Fabric API 0.156.0+26.2
- Java 25
- Gradle 9.5.1 (wrapper/bootstrap scripts included)

All versions are pinned in `gradle.properties`. The project uses Minecraft's official unobfuscated names and contains no Yarn dependency or raw OpenGL calls.

## Build

```bash
./gradlew clean build
```

Windows:

```bat
gradlew.bat clean build
```

The included auditable wrapper bootstrap (source in `tools/wrapper-src`) downloads Gradle 9.5.1 and verifies its SHA-256 checksum on first use. A successful build writes the remapped mod JAR to `build/libs/hypershot-0.2.0-beta.1.jar`.

## Install

1. Install Fabric Loader 0.19.3 for Minecraft 26.2.
2. Install Fabric API 0.156.0+26.2.
3. Optionally install Mod Menu 20.0.1 for an integrated configuration button.
4. Copy the HyperShot JAR into `.minecraft/mods/`.
5. Start Minecraft with Java 25.

## Controls

- F2: active HyperShot preset when vanilla replacement is enabled
- F9: active preset through HyperShot's dedicated key
- F6: gallery
- F7: quick capture panel
- F8: emergency cancel

All dedicated bindings are configurable in Minecraft Controls.

## Interface and Mod Menu

HyperShot uses one responsive Minecraft-native interface across quick capture, settings, gallery, preview, and diagnostics. Large windows use focused side panels; small GUI scales collapse to compact selectors and paginated controls without overlapping buttons. When Mod Menu 20.0.1 is installed, its Configure button opens the real HyperShot settings screen. Mod Menu remains optional.

## Implemented capture pipeline

- Native-sized capture through a dedicated Blaze3D render target
- Scaled off-screen capture when the requested target fits the backend limit
- Tiled capture with exact off-axis frusta and overlap cropping
- Non-divisible edge tiles
- Automatic first-tile allocation fallback to a smaller tile size
- Disk-backed RGBA assembly instead of an enormous Java heap image
- Streaming PNG row encoding with bounded memory
- JPEG through the standard JDK codec, deliberately limited to 20 million pixels because it requires a full raster
- Atomic final rename and collision-safe filenames
- Cancellation, pause/resume between tiles, recovery manifests, and cleanup
- Scoped restoration of target, projection, HUD, hand, selection outline, and window render state

A 10,000×10,000 PNG uses a roughly 400 MB disk spool but does not create a 400 MB `BufferedImage` in the Java heap.

## Gallery and preview

The gallery uses an atomic versioned JSON index. It loads only small generated thumbnails, supports search, favorites, paging, reveal, preview, soft delete, and undo. The in-game viewer deliberately shows a bounded preview and delegates actual-pixel viewing of extreme files to the operating system image viewer.

## Data layout

`minecraft/screenshots/hypershot/` contains `captures`, `sessions`, `thumbnails`, `metadata`, `temporary`, `recovery`, `presets`, `logs`, and `trash`.

## Testing

JUnit and dependency-free regression tests:

```bash
./tools/run-core-tests.sh
```

The suite covers arithmetic overflow, tile layout and projection, edge tiles, estimates, filename safety, recovery manifests, gallery querying, state restoration, streaming PNG, disk-backed assembly, thumbnail bounds, JPEG capability validation, cancellation, progress, and atomic output.

See `docs/KNOWN_LIMITATIONS.md` before using shader packs or experimental Vulkan.
