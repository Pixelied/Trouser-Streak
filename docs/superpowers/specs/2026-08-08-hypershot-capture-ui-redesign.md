# HyperShot capture crash + extreme-resolution UI redesign

Date: 2026-08-08
Target: HyperShot 0.2.x for Minecraft Java 26.2 / Fabric / Java 25
Canonical branch: `hypershot/production-26.2`

## Problem statement

HyperShot 0.2.0-beta.1 has two release-blocking problems discovered on a real macOS 26.3.2 / Apple M5 Max / Java 25 / Minecraft 26.2 installation:

1. Starting a special capture pass can crash with `IllegalAccessError` because HyperShot writes Minecraft's `final` `mainRenderTarget` field from a generated Mixin accessor method.
2. The settings UI is difficult to understand and visually broken under the tested GUI configuration: controls are too dim, hierarchy is weak, advanced values are exposed without explanation, and resolution/quality intent is not obvious.

The crash report is the authoritative reproduction for the first problem. The fix must remove the illegal field mutation rather than suppressing the exception.

## Goals

- Eliminate all writes to Minecraft's final main render-target field.
- Preserve off-screen and tiled capture without replacing vanilla-owned final fields.
- Make failure recovery exception-safe so capture errors do not leave Minecraft rendering in a corrupted state.
- Make common screenshot choices understandable without prior graphics knowledge.
- Add clear 4K, 8K, 16K, and 32K UHD presets.
- Define 32K UHD as 30,720 x 17,280.
- Keep arbitrary dimensions available through Custom.
- Explain PNG compression and JPEG quality in plain language.
- Add preflight estimates and safety classifications for extreme captures.
- Keep Mod Menu integration as the primary settings entry point.
- Preserve the existing indexed gallery, notifications, disk-backed assembly, metadata options, cancellation, and recovery unless changes are required by the crash fix.

## Non-goals

- Do not claim universal shader-pack compatibility.
- Do not claim Vulkan runtime validation without real Vulkan hardware testing.
- Do not add fake HDR, unverified render passes, or unsupported codecs.
- Do not force 32K to run when preflight checks determine the system cannot safely start it.
- Do not make JPEG the recommended archival format.

## Capture architecture

### Root cause

The current capture path uses a Mixin accessor to assign a new value to Minecraft's final main render target during `CaptureManager.renderCapturePass`. Java 25 rejects the mutation at runtime. The accessor is therefore fundamentally invalid for this field and must be removed from the capture path.

### Replacement design

HyperShot owns its render targets. Minecraft keeps ownership of its normal main render target at all times.

During an off-screen capture pass:

1. HyperShot creates or reuses a private capture render target sized for the current full frame or tile.
2. A capture-pass context is activated for the duration of exactly one render pass.
3. Calls that need the active render target are redirected at the lookup/use boundary while that context is active instead of assigning Minecraft's field.
4. The world is rendered into HyperShot's private target.
5. Pixels are copied/read into the existing bounded tile pipeline.
6. The context is cleared in `finally`, regardless of success, cancellation, or exception.
7. Vanilla rendering resumes against the untouched normal target.

The preferred implementation is the narrowest injection/redirect available at the actual render-target lookup sites used by the 26.2 renderer. A broad global redirect is not acceptable if a smaller capture-scoped hook can do the job.

### Capture context

Introduce a small client-only capture render context with these responsibilities:

- `isActive()`
- current HyperShot render target
- capture generation/session ownership
- enter/exit scope
- guard against nested or stale capture scopes

The context must be render-thread-only and should fail closed: if ownership is invalid, return the vanilla target rather than a stale HyperShot target.

### Compatibility behavior

- No direct LWJGL/OpenGL calls are introduced.
- Sodium/Iris and other render mods must never be patched by name just to make the crash disappear.
- If a shader or renderer configuration cannot safely support tiled projection, HyperShot should stop the capture with a readable compatibility error instead of crashing the game.
- A vanilla/no-shader path remains the baseline compatibility target.

## Resolution model

### Main presets

Use exact UHD-derived presets:

- Native — current framebuffer size
- 4K UHD — 3840 x 2160
- 8K UHD — 7680 x 4320
- 16K UHD — 15360 x 8640
- 32K UHD — 30720 x 17280
- Custom — user-entered width and height

32K is deliberately 30,720 x 17,280 because it is the exact 4x linear continuation of 8K UHD. A literal 32,768-pixel-wide capture remains available via Custom and must not be labeled simply "32K UHD".

### Extreme-resolution execution

Native-size captures may use the normal framebuffer when appropriate. Larger captures use HyperShot-owned off-screen targets and tiling.

The system must not require a single GPU texture equal to the final 16K or 32K output dimensions. Final output is assembled through the existing disk-backed surface.

## Preflight safety model

Before starting a non-trivial capture, compute and display:

- output width and height
- total output pixels
- tile count
- selected tile size and overlap
- estimated raw RGBA bytes
- estimated temporary disk requirement
- current free disk space
- configured disk reserve
- estimated peak heap usage
- selected output format
- approximate final-file-size range where a defensible estimate exists
- capture safety state

Safety states:

- SAFE
- LIKELY SAFE
- HIGH LOAD
- NOT RECOMMENDED
- CANNOT START

`CANNOT START` must block capture. `NOT RECOMMENDED` requires an explicit confirmation in Advanced mode. `HIGH LOAD` is allowed but clearly warns that rendering may take minutes and consume significant disk/CPU/GPU resources.

The calculation must use checked arithmetic and reject overflow.

## UI information architecture

### Simple mode

This is the default Mod Menu screen.

Sections:

1. **Resolution**
   - Native
   - 4K
   - 8K
   - 16K
   - 32K
   - Custom
   - each preset shows its exact dimensions beneath or in a tooltip

2. **Image type**
   - PNG — Maximum quality, lossless
   - JPEG — Smaller file, lossy

3. **Screenshot appearance**
   - Hide HUD
   - Hide hand
   - Hide block outline

4. **Capture estimate**
   - approximate final file size/range
   - temporary storage
   - tile count
   - estimated workload label
   - safety state

5. **Primary action**
   - Take Screenshot

Simple mode does not expose numeric PNG compression, tile size, tile overlap, metadata internals, filename templates, or memory reserve values.

### Advanced mode

Accessible through a clearly labeled Advanced button/tab.

Controls:

- exact width
- exact height
- aspect-ratio lock
- tile size
- tile overlap
- PNG save mode
- JPEG quality
- metadata privacy
- JSON metadata toggle
- disk safety reserve
- filename template
- compatibility diagnostics
- Restore recommended settings

Each advanced control has a one-sentence explanation.

## Compression terminology

### PNG

Do not present a bare 0-9 value in Simple mode.

Expose:

- Fast save
- Balanced — Recommended
- Smallest file

All three explicitly say **same image quality / lossless**. Internally they may map to encoder compression levels such as low, default, and high effort.

Advanced mode may expose the exact numeric encoder level as secondary detail if useful, but the user-facing choice remains plain language.

### JPEG

Expose a percentage-style quality control with visible text:

- JPEG is lossy.
- Lower quality reduces file size by permanently discarding detail.
- PNG is recommended for maximum-quality screenshots.

Do not call JPEG compression "quality-preserving".

## Visual design

The settings screen should look like polished Minecraft UI, not a translucent web dashboard placed over Minecraft.

- Use standard Minecraft button/edit-box interaction behavior.
- Strong readable contrast in both hovered and idle states.
- No dark overlay should cover or visually disable active widgets.
- One clear selected state for resolution and format.
- Keep borders, spacing, and section headers consistent with the rest of HyperShot.
- Avoid excessive rounded cards/pills.
- Keep the screen usable at small GUI scales through compact reflow rather than shrinking text into unreadability.
- Provide tooltips/narration for controls that need explanation.

## Error handling

Capture errors must be converted into a user-visible HyperShot failure state where possible.

On any exception during a capture pass:

- close/clear capture render context in `finally`
- restore HUD/hand/outline state
- release temporary render targets that are no longer reusable
- preserve recovery metadata only when it is valid
- log the original exception with capture session information
- show a concise notification explaining that the screenshot failed
- never continue rendering with a stale HyperShot target

## Testing plan

### Regression tests

Add a regression guard that makes it impossible for the production capture code to call a setter for Minecraft's main render target. The old accessor method should be removed or have no write path from HyperShot.

Add tests for:

- capture context enter/exit
- `finally` restoration after thrown exception
- stale-generation rejection
- resolution preset exact values
- 32K preset = 30720 x 17280
- checked-byte estimates for 8K/16K/32K
- safety-state thresholds
- UI layout at wide and compact GUI sizes
- selected/disabled control state

### Build/runtime verification

Required before release:

1. Java 25 source guards.
2. Standalone/core regression tests.
3. JUnit tests.
4. `./gradlew clean test build --stacktrace --no-daemon`.
5. JAR inspection for HyperShot and Mod Menu entrypoints.
6. Minecraft 26.2 startup smoke test.
7. Real capture test on the reported modded macOS environment, first Native/4K, then 8K.
8. At least one tiled 16K test if disk/time permits.
9. 32K must be labeled experimental/high-load until a real 32K capture completes and output dimensions/file integrity are verified.

A headless startup test alone is not enough to claim the capture crash is fixed because the failure only occurs when the capture pass is exercised.

## Release criteria

A new JAR may be called fixed when:

- the original `IllegalAccessError` path is removed;
- an actual capture completes on Java 25 without replacing Minecraft's final target;
- the settings UI is readable and its active controls are visually obvious;
- 8K tiled capture completes and writes a valid image on real hardware;
- Mod Menu opens the redesigned settings screen;
- all automated gates pass;
- known shader/Vulkan/32K limitations are still documented honestly.

A 32K preset may ship before 32K is fully validated only if it is explicitly marked high-load/experimental and preflight can block unsafe attempts. It must not be described as universally guaranteed.
