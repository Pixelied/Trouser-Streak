# Minecraft/Fabric 26.2 API audit

The client implementation was written against inspected Minecraft 26.2 official-name sources and Fabric API's `26.2` branch. Checked integration points:

- `Minecraft.renderFrame(boolean)` and the post-`GameRenderer.render(DeltaTracker, boolean)` injection point
- mutable `Minecraft.mainRenderTarget` and `deltaTracker`
- `GameRenderer.getGameRenderState`, `resize`, `render`, `renderItemInHand`, and `renderBlockOutline`
- `CameraRenderState.projectionMatrix` / `depthFar` and reversed-Z `Projection` behavior
- `MainTarget`, `RenderTarget`, `Screenshot.takeScreenshot`, and `NativeImage`
- `GuiGraphicsExtractor`, `DynamicTexture`, `TextureManager`, and 26.2 screen extraction APIs
- Fabric `HudElementRegistry`, `ScreenEvents`, `Screens`, lifecycle/tick events, and renamed `client.keymapping.v1` API

No direct `org.lwjgl.opengl` imports or raw OpenGL calls exist in project sources.
