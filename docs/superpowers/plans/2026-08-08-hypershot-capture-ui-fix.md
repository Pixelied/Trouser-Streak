# HyperShot Capture Crash + UI Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the Java 25 final-render-target crash, correct HyperShot UI draw order, add clear UHD resolution/quality controls, and introduce preflight safety for 8K/16K/32K captures without overstating untested 32K reliability.

**Architecture:** HyperShot keeps ownership of its private capture target and never assigns Minecraft's final main render-target field. A render-thread capture scope supplies the private target through a narrow GameRenderer target-lookup hook only while a HyperShot pass is active. UI chrome/content is extracted as background before vanilla widgets, while new pure-core resolution and preflight types keep presets, estimates, and safety classifications independently testable.

**Tech Stack:** Minecraft Java 26.2, Fabric Loader 0.19.3, Fabric API 0.156.0+26.2, Mod Menu 20.0.1 optional, Java 25, Loom 1.17.17, Gradle 9.5.1, official Mojang names, Mixin/MixinExtras already supplied by Fabric Loader.

## Global Constraints

- Client-only Fabric mod for Minecraft 26.2.
- Java 25 and Gradle 9.5.1.
- No direct LWJGL/OpenGL calls.
- No writes to Minecraft/GameRenderer final main render-target fields.
- 32K UHD means exactly 30,720 x 17,280; literal 32,768-wide output is Custom.
- PNG is the recommended maximum-quality lossless format; JPEG is explicitly lossy.
- Unsupported shader/Vulkan/HDR claims remain hidden or documented, never faked.
- Headless startup is necessary but not sufficient evidence that the original capture crash is fixed; a real capture must still be tested on hardware.

---

### Task 1: Capture-scope regression boundary

**Files:**
- Create: `src/client/java/dev/hypershot/capture/CaptureRenderContext.java`
- Modify: `src/client/java/dev/hypershot/mixin/MinecraftAccessor.java`
- Modify: `src/client/resources/hypershot.client.mixins.json`
- Modify: `tools/verify-project.sh`
- Test: `src/test/java/dev/hypershot/core/CoreTestMain.java`

**Interfaces:**
- Produces: `CaptureRenderContext.enter(String sessionId, RenderTarget target)` returning `CaptureRenderContext.Scope`; `CaptureRenderContext.currentTarget()` returning nullable `RenderTarget`; `Scope.close()` clears only the generation it owns.
- Removes: `MinecraftAccessor.hypershot$setMainRenderTarget(RenderTarget)`.

- [ ] **Step 1: Add a source guard that fails on the old illegal setter**

Add to `tools/verify-project.sh` a grep check that rejects `hypershot$setMainRenderTarget`, `@Mutable` access to `mainRenderTarget`, or any assignment accessor targeting `mainRenderTarget` under `src/client/java/dev/hypershot`.

- [ ] **Step 2: Run the verification script and confirm it fails on current source**

Run: `./tools/verify-project.sh`
Expected: FAIL because `MinecraftAccessor` still contains the mutable setter.

- [ ] **Step 3: Implement the render-thread capture context**

Create `CaptureRenderContext` as a final client-only utility with one active scope at a time. `enter` rejects null target/session IDs and nested scopes; each scope stores a monotonically increasing generation. `currentTarget` returns the target only while a scope is active. `close` clears the state only when the closing generation still owns it, preventing stale scope cleanup from clearing a newer scope.

- [ ] **Step 4: Remove the mutable main-target accessor**

Keep only the `deltaTracker` getter in `MinecraftAccessor`; remove the `RenderTarget` import, `@Mutable`, and `hypershot$setMainRenderTarget` method.

- [ ] **Step 5: Run standalone/core verification**

Run: `./tools/run-core-tests.sh && ./tools/verify-project.sh`
Expected: PASS.

- [ ] **Step 6: Commit**

Commit message: `fix: remove illegal main render target mutation`

---

### Task 2: Capture-scoped GameRenderer target lookup

**Files:**
- Create: `src/client/java/dev/hypershot/mixin/GameRendererTargetMixin.java`
- Modify: `src/client/resources/hypershot.client.mixins.json`
- Modify: `src/client/java/dev/hypershot/capture/CaptureManager.java`

**Interfaces:**
- Consumes: `CaptureRenderContext.currentTarget()` and `CaptureRenderContext.enter(...)`.
- Produces: while the scope is active, `GameRenderer.mainRenderTarget()` returns HyperShot's private target; outside the scope it returns vanilla's untouched target.

- [ ] **Step 1: Add the target-lookup mixin**

Inject at `HEAD` of `GameRenderer.mainRenderTarget()` with a cancellable `CallbackInfoReturnable<RenderTarget>`. If `CaptureRenderContext.currentTarget()` is non-null, return it; otherwise do nothing and let vanilla return its final field.

- [ ] **Step 2: Register the mixin**

Add `GameRendererTargetMixin` to `hypershot.client.mixins.json` with no optional renderer-mod-specific mixins.

- [ ] **Step 3: Rewrite `CaptureManager.renderCapturePass` around a scope**

Get the vanilla target before entering the context. Call `ensureCaptureTarget`. Then execute extraction/render/readback inside `try (CaptureRenderContext.Scope ignored = CaptureRenderContext.enter(session.id, captureTarget))`. Remove both old main-target setter calls. Keep projection/HUD/block-outline restoration in `finally`. After the scope closes, resize/extract against the vanilla target and clear `inCapturePass`.

- [ ] **Step 4: Make restoration fail closed**

If rendering or restoration fails, log the original exception, ensure the context scope closes, restore mutable UI/camera state in `finally`, and never leave `inCapturePass` true.

- [ ] **Step 5: Compile on Java 25 CI**

Run: `./gradlew clean test build --stacktrace --no-daemon`
Expected: PASS with official names and no raw OpenGL guard failures.

- [ ] **Step 6: Commit**

Commit message: `fix: route capture target without final field writes`

---

### Task 3: Fix HyperShot screen draw order

**Files:**
- Modify: `src/client/java/dev/hypershot/ui/HyperShotScreen.java`
- Modify: `src/client/java/dev/hypershot/ui/QuickCaptureScreen.java`
- Modify: `src/client/java/dev/hypershot/ui/SettingsScreen.java`
- Modify: `src/client/java/dev/hypershot/ui/GalleryScreen.java`
- Modify: `src/client/java/dev/hypershot/ui/ImageViewerScreen.java`
- Modify: `src/client/java/dev/hypershot/ui/DiagnosticsScreen.java`

**Interfaces:**
- Produces: `HyperShotScreen.extractHyperShotBackground(GuiGraphicsExtractor,int,int,float)` template hook called from `extractBackground` before vanilla widgets are extracted.

- [ ] **Step 1: Add the background-content template hook**

`HyperShotScreen.extractBackground` draws the base gradient, then calls a protected no-op `extractHyperShotBackground(...)` hook.

- [ ] **Step 2: Move each screen's non-widget drawing into the hook**

For Quick Capture, Settings, Gallery, Viewer, and Diagnostics, replace their `extractRenderState` overrides with `extractHyperShotBackground` overrides. Do not call `super.extractRenderState` from these hooks. This guarantees chrome, cards, labels, and panels are drawn before Minecraft buttons/edit boxes.

- [ ] **Step 3: Verify layout tests and compile**

Run: `./tools/run-core-tests.sh && ./gradlew test --no-daemon`
Expected: PASS.

- [ ] **Step 4: Commit**

Commit message: `fix: draw HyperShot panels behind widgets`

---

### Task 4: UHD resolution model and exact presets

**Files:**
- Create: `src/main/java/dev/hypershot/core/ResolutionPreset.java`
- Modify: `src/client/java/dev/hypershot/config/HyperShotConfig.java`
- Modify: `src/client/java/dev/hypershot/config/CapturePreset.java`
- Test: `src/test/java/dev/hypershot/core/CoreTestMain.java`

**Interfaces:**
- Produces: `ResolutionPreset` values `NATIVE`, `UHD_4K`, `UHD_8K`, `UHD_16K`, `UHD_32K`, `CUSTOM`; `fixedResolution()` returns null for Native/Custom and exact `Resolution` for fixed UHD presets.

- [ ] **Step 1: Add failing exact-resolution assertions**

Assert 4K=`3840x2160`, 8K=`7680x4320`, 16K=`15360x8640`, and 32K=`30720x17280`.

- [ ] **Step 2: Implement `ResolutionPreset`**

Each enum value has display label and optional fixed dimensions. No fuzzy aliases.

- [ ] **Step 3: Replace misleading square defaults**

Keep existing user-created presets, but built-in presets become Native, 4K UHD, 8K UHD, 16K UHD, 32K UHD, Phone Wallpaper, Wallpaper, and Social JPEG. Do not label square 16,384 or 20,000 outputs as 16K/20K UHD.

- [ ] **Step 4: Run core tests**

Run: `./tools/run-core-tests.sh`
Expected: PASS including exact 32K dimensions.

- [ ] **Step 5: Commit**

Commit message: `feat: add exact UHD capture presets`

---

### Task 5: Preflight safety model

**Files:**
- Create: `src/main/java/dev/hypershot/core/CaptureSafetyState.java`
- Create: `src/main/java/dev/hypershot/core/CapturePreflight.java`
- Create: `src/main/java/dev/hypershot/core/CapturePreflightCalculator.java`
- Modify: `src/client/java/dev/hypershot/capture/HardwareCapabilityScanner.java`
- Modify: `src/client/java/dev/hypershot/capture/CaptureManager.java`
- Test: `src/test/java/dev/hypershot/core/CoreTestMain.java`

**Interfaces:**
- Produces: `CapturePreflight` containing estimate, tile count, raw bytes, usable disk, disk reserve, and `CaptureSafetyState`.
- Produces: `CapturePreflightCalculator.calculate(CaptureSpec spec, long usableDiskBytes, long freeHeapBytes, long diskReserveBytes)`.

- [ ] **Step 1: Add failing safety tests**

Cover checked 8K/16K/32K byte estimates, `CANNOT_START` when temporary bytes exceed usable disk, `HIGH_LOAD` for 32K under otherwise adequate resources, and overflow rejection.

- [ ] **Step 2: Implement deterministic safety classification**

Use checked arithmetic. `CANNOT_START` blocks disk/heap impossibility; `NOT_RECOMMENDED` is near hard limits; `HIGH_LOAD` covers very large pixel/tile workloads such as 32K; `LIKELY_SAFE` and `SAFE` cover lower-risk captures with healthy margins.

- [ ] **Step 3: Use the calculator in `CaptureManager.start`**

Replace the ad-hoc temporary-disk calculation with preflight. If state is `CANNOT_START`, fail before creating session files. Keep encoder-specific validation.

- [ ] **Step 4: Run tests**

Run: `./tools/run-core-tests.sh && ./gradlew test --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: add extreme capture preflight safety`

---

### Task 6: Simple settings UI with Advanced mode

**Files:**
- Rewrite: `src/client/java/dev/hypershot/ui/SettingsScreen.java`
- Modify: `src/client/java/dev/hypershot/ui/HyperShotTheme.java`
- Modify: `src/client/resources/assets/hypershot/lang/en_us.json`
- Modify: `src/client/java/dev/hypershot/HyperShotClient.java`

**Interfaces:**
- Simple mode: Resolution, Image Type, Appearance, live Estimate, primary Capture action.
- Advanced mode: exact dimensions, tile size/overlap, PNG save effort, JPEG quality, metadata, disk reserve, diagnostics/reset.

- [ ] **Step 1: Replace section-cycling settings with Simple/Advanced modes**

Default Simple view presents one readable vertical flow. Resolution buttons are Native / 4K / 8K / 16K / 32K / Custom with selected state. Image type uses `PNG — Lossless` and `JPEG — Smaller, lossy`. Appearance toggles are plain `HUD`, `Hand`, and `Block outline` include/hide choices.

- [ ] **Step 2: Replace raw PNG compression number in Simple mode**

Expose `Fast save`, `Balanced — Recommended`, `Smallest file`; map internally to stable numeric levels. Always state `Same image quality — PNG is lossless`.

- [ ] **Step 3: Explain JPEG quality**

Advanced JPEG quality shows percentage and the sentence `JPEG is lossy; lower quality permanently discards detail.` PNG remains recommended for archival/extreme screenshots.

- [ ] **Step 4: Add live preflight summary**

Show exact resolution, total megapixels, tile count, estimated temporary storage, approximate final size, and safety label. Disable capture on `CANNOT_START`; 32K displays `Experimental / High load` until real 32K validation exists.

- [ ] **Step 5: Keep Mod Menu entrypoint unchanged**

`HyperShotModMenuApi` continues opening this redesigned `SettingsScreen`; no new hard dependency on Mod Menu.

- [ ] **Step 6: Compile and inspect at compact/wide layouts**

Run: `./gradlew clean test build --stacktrace --no-daemon` and layout tests.
Expected: PASS with no overlay covering widgets.

- [ ] **Step 7: Commit**

Commit message: `feat: simplify extreme screenshot settings`

---

### Task 7: Release verification and hardware-test candidate

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `docs/KNOWN_LIMITATIONS.md`
- Modify: `.github/workflows/build.yml` only if needed to include the fix branch during verification.

**Interfaces:**
- Produces: CI artifact for a `0.2.1-rc` hardware-test candidate; does not claim full fix until real capture succeeds.

- [ ] **Step 1: Run complete automated gate on Java 25**

Required: source guards, standalone core tests, JUnit, `clean test build`, JAR inspection, Mod Menu entrypoint check, and Minecraft 26.2 startup under Xvfb.

- [ ] **Step 2: Inspect the built JAR**

Confirm there is no `hypershot$setMainRenderTarget` symbol and that `GameRendererTargetMixin` plus the redesigned Settings screen are present.

- [ ] **Step 3: Publish a hardware-test candidate**

Artifact filename: `HyperShot-0.2.1-rc1+mc26.2.jar` (or next rc number if CI corrections are required).

- [ ] **Step 4: Real-hardware acceptance sequence**

On the reported Java 25/macOS modpack: test Native first, then 4K, then 8K. Verify Minecraft remains alive, file dimensions are exact, image opens successfully, and normal rendering resumes after capture. Attempt 16K only after 8K is clean. Keep 32K marked experimental until one full 30,720 x 17,280 output is verified.

- [ ] **Step 5: Final release decision**

Only after the original capture path is exercised successfully on hardware may the candidate be promoted from RC to fixed release.
