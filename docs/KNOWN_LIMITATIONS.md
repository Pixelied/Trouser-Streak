# Known limitations — 0.1.0-alpha.1

- OpenGL code paths are source-audited against Minecraft 26.2. Vulkan uses the same Blaze3D APIs, but no Vulkan runtime was available in the build environment for an end-to-end game test.
- Shader-pack screen-space effects can produce tile-local history or exposure differences. HyperShot does not claim universal Iris/shader compatibility.
- Pause/resume works between tiles. Encoding is not currently interruptible mid-row once finalization begins.
- Recovery manifests are written and preserved during active work, but automatic restart/resume after a game relaunch is not exposed yet.
- JPEG is limited to 20 million pixels to avoid a full extreme-resolution heap raster. Extreme output should use PNG.
- WebP, TIFF/BigTIFF, EXR, QOI, true HDR/16-bit, temporal accumulation, supersampling, panoramas, cubemaps, orthographic capture, and auxiliary render passes are deliberately absent from the UI rather than faked.
- Per-HUD-element hiding, entity/player masks, weather freezing, transparent world backgrounds, chunk preloading, burst/interval capture, batch conversion, tags/ratings editing UI, multi-select, and system sharing are not implemented in this alpha.
- The in-game viewer uses generated previews. Actual-pixel viewing of huge captures is delegated to the OS viewer to avoid decoding multi-gigabyte images in Minecraft.
- Native mode currently performs a clean same-size rerender so capture-only HUD hiding works without visible flicker; it is not a zero-cost copy of the already-presented framebuffer.
