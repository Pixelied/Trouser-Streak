# HyperShot

HyperShot is a client-only Fabric screenshot and photography system for Minecraft Java Edition 26.2. The current 0.2.1 release-candidate line combines real off-screen/tiled rendering with an in-world camera workflow: extreme-resolution PNG capture, safety preflight, Photo/Burst/Time/Cinematic modes, a hybrid F2 viewfinder, grouped Gallery sessions, metadata, diagnostics, and restoration-aware scene preparation.

## Requirements

- Minecraft Java Edition 26.2
- Fabric Loader 0.19.3
- Fabric API 0.156.0+26.2
- Java 25
- Gradle 9.5.1
- Optional: Mod Menu 20.0.1

All versions are pinned in `gradle.properties`. The project uses Minecraft's official unobfuscated names and contains no Yarn dependency or raw OpenGL capture path.

## Build

```bash
./gradlew clean test build --stacktrace --no-daemon
```

Windows:

```bat
gradlew.bat clean test build --stacktrace --no-daemon
```

The included auditable wrapper bootstrap (source in `tools/wrapper-src`) downloads the pinned Gradle distribution and verifies its checksum on first use. A successful rc2 build writes the remapped mod JAR to `build/libs/hypershot-0.2.1-rc2.jar`.

## Install

1. Install Fabric Loader 0.19.3 for Minecraft 26.2.
2. Install Fabric API 0.156.0+26.2.
3. Optionally install Mod Menu 20.0.1 for an integrated Configure button.
4. Copy the HyperShot JAR into `.minecraft/mods/`.
5. Start Minecraft with Java 25.

## Controls

- F2: by default, tap for a queued Photo; hold to open the in-world HyperShot camera viewfinder. While the viewfinder is open, a short F2 press is the shutter for the selected camera mode.
- F7: opens the cursor-enabled camera controls while the viewfinder is active; outside the camera it opens Quick Capture.
- F9: dedicated immediate active-preset capture.
- F6: open Gallery.
- F8: emergency cancel/restore path.

Dedicated bindings are configurable in Minecraft Controls. F2 behavior and hold threshold are configurable from Camera Behavior.

## Camera workflow

The collapsed viewfinder is a HUD overlay rather than a normal Minecraft `Screen`, so normal mouselook and movement remain available while composing. The overlay, guides, and camera chrome suppress themselves during HyperShot's actual render pass and therefore are not baked into the output image.

HyperShot currently provides four camera modes:

- **Photo** — one deliberate image using the active resolution/format preset. Timer, composition guides, and Shader Settle can prepare the frame before capture.
- **Burst** — a configurable sequence captured one image at a time. Frames share a capture-group identity and can be browsed as a grouped Gallery/contact-sheet session. Sequential capture keeps peak memory bounded even at extreme resolutions.
- **Time** — singleplayer lighting brackets built on Minecraft 26.2's native `WorldClock` system. HyperShot snapshots the exact original clock value, applies each requested lighting state, optionally settles shaders, captures the sequence, and restores the original clock afterward.
- **Cinematic** — restoration-aware scene preparation with camera/FOV lock, Clean Frame, nearby-chunk readiness, optional integrated-server time/weather control, and optional vanilla world freeze. Server-owned controls are disabled on multiplayer rather than being simulated client-side.

If Cinematic nearby-chunk readiness reaches its timeout, HyperShot blocks the shot and exposes explicit **Capture anyway** and **Cancel shot** actions instead of silently taking an incomplete frame.

## Resolution and image quality

The Simple capture presets are:

- Native
- 4K UHD — 3840×2160
- 8K UHD — 7680×4320
- 16K UHD — 15360×8640
- 32K UHD — 30720×17280 (experimental/high-load)
- Custom

PNG is lossless. Its Fast/Balanced/Smallest-file save choices change encoding effort and file size, not visual quality. JPEG is lossy and deliberately bounded because the current JPEG encoder requires a full buffered raster; use PNG for extreme-resolution maximum-quality output.

Before a capture begins, HyperShot estimates resolution, pixels, tiles, temporary storage, expected final size, available disk/heap resources, and a safety state. `Cannot start` is blocked. Very high-load/unsafe work is surfaced rather than hidden.

## Capture pipeline

- Native-sized clean rerender through HyperShot's dedicated Blaze3D target.
- Scaled off-screen capture when the requested target fits the backend limit.
- Tiled capture using off-axis projection and overlap cropping.
- Non-divisible edge tiles and automatic first-tile allocation fallback.
- Disk-backed RGBA assembly instead of requiring a giant Java heap image.
- Streaming PNG row encoding with bounded memory.
- Bounded standard-JDK JPEG encoding.
- Atomic final rename and collision-safe filenames.
- Cancellation, pause/resume between tiles, recovery manifests, and cleanup.
- Scoped restoration of capture target context, projection, HUD, hand, selection outline, camera preparation, and temporary scene state.

The Java 25 crash from the old mutable `mainRenderTarget` path is guarded against: HyperShot no longer writes Minecraft's final main render-target field. Capture-scoped lookups are redirected to HyperShot's private target only while a capture pass is active.

## Interface and Mod Menu

HyperShot uses a responsive Minecraft-native interface across Quick Capture, capture settings, Camera Behavior, Gallery, grouped sessions, preview, and diagnostics. Simple capture settings prioritize resolution, image type, appearance, and preflight. Advanced settings expose exact dimensions, tile geometry, save effort/JPEG quality, metadata/privacy, disk reserve, and diagnostics.

Camera Behavior is split into focused pages for Photography, Sequences, Cinematic, and F2/Viewfinder interaction. Mod Menu 20.0.1 opens the real HyperShot settings screen and remains optional.

## Gallery and preview

The Gallery uses an atomic versioned JSON index and bounded generated thumbnails. It supports search, favorites, paging, reveal, preview, soft delete, and undo. Burst/Time captures can share group metadata and grouped contact-sheet browsing. Extreme files are previewed at safe dimensions; actual-pixel viewing is delegated to the operating-system image viewer.

## Data layout

`minecraft/screenshots/hypershot/` contains `captures`, `sessions`, `thumbnails`, `metadata`, `temporary`, `recovery`, `presets`, `logs`, and `trash`.

## Testing and release status

Run the dependency-free verification suite with:

```bash
./tools/run-core-tests.sh
./tools/verify-project.sh
```

The rc2 branch currently covers 212 dependency-free assertions across the original capture core plus camera preparation, F2 gesture semantics, guide geometry, overlay fading, sequence arithmetic, Time continuation, Cinematic capability gating, orchestration order, and restoration races. CI additionally runs Java 25 Gradle tests/build, production-JAR inspection, and a Minecraft 26.2 development-client startup smoke.

Those automated gates do **not** substitute for an actual in-world screenshot pass. `0.2.1-rc2` is a hardware-test release candidate. Native/4K/8K Photo plus Burst/Time/Cinematic capture and restoration must be exercised on the target installation before promotion. 32K remains experimental, and Cinematic world freeze stays off by default until a real freeze/capture/restore cycle is verified.

See `docs/KNOWN_LIMITATIONS.md` before testing shader packs, Vulkan, 32K, or Cinematic world-state controls.
