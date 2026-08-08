# Architecture

## Threading

Minecraft objects and all rendering stay on the render thread. GPU readback completion copies one bounded tile into a plain byte array. Disk writes, manifests, image encoding, metadata, index updates, and thumbnail decoding run on dedicated worker executors. No worker touches live renderer state.

## Render state scope

`CaptureManager.renderCapturePass` snapshots the main target, render dimensions, HUD flag, camera projection, and block-outline flag. It restores every field in `finally`, including after allocation or shader failures. The hand mixin only suppresses the first-person hand while `inCapturePass` is true.

## Tiling

`TileLayout` creates output-space content rectangles and expanded render rectangles. `TileProjectionCalculator` maps each render rectangle into the complete camera frustum. Overlap pixels are rendered but cropped while writing to `DiskBackedRgbaSurface`, preventing duplicated borders and reducing screen-space seam artifacts.

## Memory and storage

Each tile is read back independently. The final output surface is a sparse/random-access RGBA file sized with checked 64-bit arithmetic. PNG encoding reads one row at a time. The producer/consumer boundary is bounded by one GPU tile byte array and one encoder row, not the full image.

## Output

`ImageEncoder` provides capability validation before capture. PNG is streaming and supports extreme dimensions permitted by PNG's integer fields and available storage. JPEG is real but bounded because the bundled ImageIO writer requires a complete raster. Temporary output is moved into place only after successful encoding.

## Persistence

Configuration, gallery index, recovery manifests, and metadata use human-readable JSON. Writes go through a sibling temporary file, retain the previous valid file as `.bak`, and use atomic replacement where supported.
