# HyperShot Design

HyperShot is a client-only Minecraft 26.2 Fabric screenshot system. The production boundary is split into a dependency-free capture core and a thin Minecraft client adapter.

## Reliability invariants

1. Every dimension and byte-count multiplication is checked before allocation.
2. Extreme captures use bounded tile buffers and stream scanlines into the encoder; the final image is never retained in Java heap.
3. Final paths are created only by atomic rename from a temporary file.
4. Modified game state is held by an `AutoCloseable` scope and restored in reverse order even on cancellation or failure.
5. Rendering stays on the render thread. Encoding, metadata, indexing, and thumbnail work stay on bounded workers.
6. Features that lack honest backend data are hidden rather than simulated.

## Renderer integration

Native capture uses Minecraft's backend-independent `Screenshot.takeScreenshot(RenderTarget, Consumer<NativeImage>)` path. High-resolution capture uses Blaze3D `TextureTarget`/`RenderTarget`, projection-window math, and Minecraft's render loop hooks. No raw OpenGL symbols are referenced.

Tiled output computes an off-axis perspective frustum for each crop-expanded tile. Tiles are read back asynchronously, cropped to their non-overlap content rectangles, then committed to a disk-backed row spool. A streaming PNG encoder consumes complete rows. This allows outputs above a backend's maximum single render-target dimension.

Shader packs and screen-space post effects can require overlap larger than their sampling radius. HyperShot reports incompatibility when a known adapter cannot guarantee seam-safe output.

## Honest feature gates

PNG and JPEG are exposed in the first release. PNG supports full streaming for huge images. JPEG is limited to captures that fit the bounded in-memory encoder budget because the JDK ImageIO writer does not offer a dependable tile-streaming contract. HDR, EXR, higher bit depth, transparent world backgrounds, normals/ID passes, and Vulkan-specific capability reporting remain hidden unless an adapter can prove real source data and successful support.
