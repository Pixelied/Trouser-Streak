# Known limitations — 0.2.1-rc1

- This build is a hardware-test release candidate. Java 25 compilation, source/JAR guards, automated tests, and Minecraft 26.2 startup are required gates, but they do not exercise the actual screenshot capture pass. The originally failing macOS modpack must still complete Native/4K/8K captures before this RC is promoted to a final release.
- The old `hypershot$setMainRenderTarget` final-field write has been removed. The replacement routes `GameRenderer.mainRenderTarget()` through a capture-scoped private HyperShot target. Real-hardware capture testing is still required to prove the full renderer/mod interaction path in the user's installation.
- 32K UHD means 30,720×17,280. It is intentionally marked experimental/high-load until a complete 32K capture is produced and its dimensions/file integrity are verified on real hardware.
- OpenGL compilation and client startup are verified against Minecraft 26.2. Vulkan uses the same Blaze3D-facing architecture, but no Vulkan runtime was available for an end-to-end capture test.
- Shader-pack screen-space effects can produce tile-local history or exposure differences. HyperShot does not claim universal Iris/shader compatibility. The reported crash occurred with Iris present but the shader pack disabled.
- Pause/resume works between tiles. Encoding is not currently interruptible mid-row once finalization begins.
- Recovery manifests are written and preserved during active work, but automatic restart/resume after a game relaunch is not exposed yet.
- JPEG is limited to 20 million pixels to avoid a full extreme-resolution heap raster. Extreme output should use PNG.
- PNG compression is lossless at every supported level. Higher compression effort may save disk space at the cost of encoding time; it does not improve image quality.
- WebP, TIFF/BigTIFF, EXR, QOI, true HDR/16-bit, temporal accumulation, supersampling, panoramas, cubemaps, orthographic capture, and auxiliary render passes are deliberately absent from the UI rather than faked.
- Per-HUD-element hiding, entity/player masks, weather freezing, transparent world backgrounds, chunk preloading, burst/interval capture, batch conversion, tags/ratings editing UI, multi-select, and system sharing are not implemented in this RC.
- The in-game viewer uses generated previews. Actual-pixel viewing of huge captures is delegated to the OS viewer to avoid decoding multi-gigabyte images in Minecraft.
- Native mode performs a clean same-size rerender so capture-only HUD hiding works without visible flicker; it is not a zero-cost copy of the already-presented framebuffer.
