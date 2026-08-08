package dev.hypershot.capture;

import dev.hypershot.core.CancellationToken;
import dev.hypershot.core.CapturePhase;
import dev.hypershot.core.CaptureProgress;
import dev.hypershot.core.DiskBackedRgbaSurface;
import dev.hypershot.core.EncoderOptions;
import dev.hypershot.core.ImageEncoder;
import dev.hypershot.core.RecoveryManifest;
import dev.hypershot.core.Tile;
import dev.hypershot.core.TileLayout;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

final class CaptureSession implements AutoCloseable {
    final String id = UUID.randomUUID().toString();
    final CaptureRequest request;
    TileLayout layout;
    final Path spoolPath;
    final Path recoveryPath;
    final Path temporaryImage;
    final Path finalImage;
    final Path metadataPath;
    final Path thumbnailPath;
    final ImageEncoder encoder;
    final EncoderOptions encoderOptions;
    final DiskBackedRgbaSurface surface;
    final CancellationToken cancellation = new CancellationToken();
    CaptureProgress progress;
    final Instant startedAt = Instant.now();
    RecoveryManifest recovery;
    int nextTileIndex;
    boolean waitingForReadback;
    boolean finalizing;
    boolean paused;

    CaptureSession(CaptureRequest request, TileLayout layout, Path spoolPath, Path recoveryPath,
                   Path temporaryImage, Path finalImage, Path metadataPath, Path thumbnailPath,
                   ImageEncoder encoder, EncoderOptions encoderOptions) throws IOException {
        this.request = request;
        this.layout = layout;
        this.spoolPath = spoolPath;
        this.recoveryPath = recoveryPath;
        this.temporaryImage = temporaryImage;
        this.finalImage = finalImage;
        this.metadataPath = metadataPath;
        this.thumbnailPath = thumbnailPath;
        this.encoder = encoder;
        this.encoderOptions = encoderOptions;
        this.surface = new DiskBackedRgbaSurface(spoolPath, layout.width(), layout.height());
        this.recovery = RecoveryManifest.create(id, spoolPath, layout.columns(), layout.rows());
        this.progress = new CaptureProgress(layout.tiles().size(), layout.totalPixels());
        this.progress.begin(CapturePhase.PREPARING_SCENE, System.nanoTime());
    }

    Tile currentTile() {
        return layout.tiles().get(nextTileIndex);
    }

    boolean hasMoreTiles() {
        return nextTileIndex < layout.tiles().size();
    }

    @Override
    public void close() throws IOException {
        surface.close();
    }
}
