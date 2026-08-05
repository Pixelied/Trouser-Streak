package dev.hypershot.capture;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.hypershot.config.MetadataPrivacy;
import dev.hypershot.core.AtomicOutput;
import dev.hypershot.core.CapturePhase;
import dev.hypershot.core.CaptureProgressSnapshot;
import dev.hypershot.core.CheckedMath;
import dev.hypershot.core.FilenamePolicy;
import dev.hypershot.core.EncoderOptions;
import dev.hypershot.core.ImageEncoder;
import dev.hypershot.core.ImageEncoders;
import dev.hypershot.core.ProjectionWindow;
import dev.hypershot.core.Tile;
import dev.hypershot.core.TileLayout;
import dev.hypershot.core.TileProjectionCalculator;
import dev.hypershot.core.ThumbnailGenerator;
import dev.hypershot.mixin.GameRendererAccessor;
import dev.hypershot.mixin.MinecraftAccessor;
import dev.hypershot.util.AtomicJson;
import dev.hypershot.util.HyperShotPaths;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CaptureManager implements AutoCloseable {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss.SSS", Locale.ROOT);

    private final Logger logger;
    private final HyperShotPaths paths;
    private final HardwareCapabilityScanner capabilityScanner;
    private final MetadataWriter metadataWriter;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "HyperShot Encoder");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean inCapturePass = new AtomicBoolean();
    private volatile CaptureListener listener = new CaptureListener() {};
    private volatile CaptureSession active;
    private MainTarget captureTarget;
    private long freeDiskMarginBytes;
    private MetadataPrivacy privacy;

    public CaptureManager(Logger logger, HyperShotPaths paths, long freeDiskMarginBytes, MetadataPrivacy privacy) {
        this.logger = Objects.requireNonNull(logger);
        this.paths = Objects.requireNonNull(paths);
        this.freeDiskMarginBytes = freeDiskMarginBytes;
        this.privacy = Objects.requireNonNull(privacy);
        this.capabilityScanner = new HardwareCapabilityScanner();
        this.metadataWriter = new MetadataWriter();
    }

    public void setListener(CaptureListener listener) {
        this.listener = Objects.requireNonNull(listener);
    }

    public synchronized void configure(long freeDiskMarginBytes, MetadataPrivacy privacy) {
        this.freeDiskMarginBytes = Math.max(256L * 1024 * 1024, freeDiskMarginBytes);
        this.privacy = Objects.requireNonNull(privacy);
    }

    public synchronized boolean isActive() {
        return active != null;
    }

    public synchronized CaptureProgressSnapshot progress() {
        return active == null ? null : active.progress.snapshot(System.nanoTime());
    }

    public synchronized void start(Minecraft minecraft, CaptureRequest request) {
        RenderSystem.assertOnRenderThread();
        if (active != null) throw new IllegalStateException("A HyperShot capture is already active");
        try {
            var capabilities = capabilityScanner.scan(paths.captures(), freeDiskMarginBytes);
            ImageEncoder encoder = ImageEncoders.forFormat(request.outputFormat());
            long availableEncoderHeap = Math.max(64L * 1024 * 1024,
                    Math.min(512L * 1024 * 1024, capabilities.freeHeapBytes() / 2));
            EncoderOptions encoderOptions = new EncoderOptions(request.pngCompression(), request.jpegQuality(), true, availableEncoderHeap);
            encoder.validate(request.resolution().width(), request.resolution().height(), encoderOptions);
            long rgbaBytes = CheckedMath.bytesForPixels(request.resolution().width(), request.resolution().height(), 4);
            long worstCaseTemporary = CheckedMath.add(rgbaBytes, rgbaBytes);
            if (capabilities.usableDiskBytes() < worstCaseTemporary) {
                throw new IOException("Not enough free disk space. Need about " + humanBytes(worstCaseTemporary)
                        + " after the configured safety margin; available " + humanBytes(capabilities.usableDiskBytes()));
            }

            int maxTexture = capabilities.maxRenderTargetDimension();
            long readbackBudget = Math.max(64L * 1024 * 1024, capabilities.freeHeapBytes() / 3);
            long safeReadbackPixels = Math.max(512L * 512L, readbackBudget / 8L); // int[] + RGBA byte[]; NativeImage stays native.
            int safeSingleDimension = Math.max(512, Math.min(maxTexture, (int) Math.floor(Math.sqrt(safeReadbackPixels))));
            boolean singleTargetFits = request.resolution().width() <= maxTexture
                    && request.resolution().height() <= maxTexture
                    && CheckedMath.multiply(request.resolution().width(), request.resolution().height()) <= safeReadbackPixels;
            int effectiveTileSize;
            int overlap;
            if (request.mode() != dev.hypershot.config.CaptureMode.TILED && singleTargetFits) {
                effectiveTileSize = Math.max(request.resolution().width(), request.resolution().height());
                overlap = 0;
            } else {
                effectiveTileSize = Math.min(Math.min(request.tileSize(), maxTexture), safeSingleDimension);
                overlap = Math.min(request.overlap(), Math.max(0, effectiveTileSize / 2 - 1));
            }
            effectiveTileSize = Math.max(256, effectiveTileSize);
            TileLayout layout = TileLayout.create(request.resolution().width(), request.resolution().height(), effectiveTileSize, overlap);

            String stem = FilenamePolicy.sanitizeStem(FILE_TIME.format(OffsetDateTime.now()) + "_" + request.presetName()
                    + "_" + request.resolution().width() + "x" + request.resolution().height());
            Path finalImage = AtomicOutput.uniqueSibling(paths.captures().resolve(stem + encoder.extension()));
            Path temporaryImage = paths.temporary().resolve("." + finalImage.getFileName() + ".part");
            Path spool = paths.temporary().resolve(stem + ".rgba");
            Path recovery = paths.recovery().resolve(stem + ".json");
            Path metadata = paths.metadata().resolve(stem + ".json");
            Path thumbnail = paths.thumbnails().resolve(stem + ".png");
            active = new CaptureSession(request, layout, spool, recovery, temporaryImage, finalImage, metadata, thumbnail, encoder, encoderOptions);
            AtomicJson.write(recovery, active.recovery.toJson());
            active.progress.setPhase(CapturePhase.CAPTURING_TILES);
            listener.onStarted(active.id, request);
        } catch (Throwable error) {
            logger.error("Unable to start HyperShot capture", error);
            listener.onFailed("not-started", error.getMessage(), error);
        }
    }

    public synchronized void cancel(String reason) {
        if (active != null) active.cancellation.cancel(reason);
    }

    public synchronized boolean isPaused() { return active != null && active.paused; }

    public synchronized void pause() {
        if (active == null || active.finalizing || active.paused) return;
        active.paused = true;
        active.progress.setPhase(CapturePhase.PAUSED);
        listener.onProgress(active.id, active.progress.snapshot(System.nanoTime()));
    }

    public synchronized void resume() {
        if (active == null || !active.paused) return;
        active.paused = false;
        active.progress.setPhase(CapturePhase.CAPTURING_TILES);
        listener.onProgress(active.id, active.progress.snapshot(System.nanoTime()));
    }

    /** Called immediately after Minecraft has rendered the normal frame, before it is presented. */
    public void renderCapturePass(Minecraft minecraft, boolean advanceGameTime) {
        CaptureSession session = active;
        if (session == null || session.waitingForReadback || session.finalizing || session.paused) return;
        if (session.cancellation.isCancelled()) {
            finishCancelled(minecraft, session);
            return;
        }
        if (!session.hasMoreTiles()) {
            beginFinalize(minecraft, session);
            return;
        }
        if (!inCapturePass.compareAndSet(false, true)) return;

        RenderTarget originalTarget = minecraft.gameRenderer.mainRenderTarget();
        GameRenderer renderer = minecraft.gameRenderer;
        GameRenderState state = renderer.gameRenderState();
        WindowRenderState windowState = state.windowRenderState;
        GuiRenderState guiState = state.guiRenderState;
        CameraRenderState cameraState = state.levelRenderState.cameraRenderState;
        Tile tile = session.currentTile();

        int oldWidth = windowState.width;
        int oldHeight = windowState.height;
        boolean oldHudHidden = guiState.isHudHidden;
        Matrix4f oldProjection = new Matrix4f(cameraState.projectionMatrix);
        boolean oldBlockOutline = ((GameRendererAccessor) renderer).hypershot$getRenderBlockOutline();

        try {
            ensureCaptureTarget(tile.renderWidth(), tile.renderHeight());
            ((GameRendererAccessor) renderer).hypershot$setMainRenderTarget(captureTarget);
            renderer.resize(tile.renderWidth(), tile.renderHeight());
            // Re-extract through the real 26.2 renderer while the capture-pass guard is active.
            // This excludes HyperShot's own HUD element and refreshes GUI/world render state without advancing simulation.
            renderer.extract(((MinecraftAccessor) minecraft).hypershot$getDeltaTracker(), advanceGameTime);
            windowState.width = tile.renderWidth();
            windowState.height = tile.renderHeight();
            if (session.request.hideHud()) guiState.reset();
            guiState.isHudHidden = session.request.hideHud();
            ((GameRendererAccessor) renderer).hypershot$setRenderBlockOutline(oldBlockOutline && !session.request.hideBlockOutline());
            applyTileProjection(session, tile, cameraState);

            renderer.render(((MinecraftAccessor) minecraft).hypershot$getDeltaTracker(), advanceGameTime);
            session.waitingForReadback = true;
            Screenshot.takeScreenshot(captureTarget, image -> acceptTileReadback(minecraft, session, tile, image));
        } catch (Throwable error) {
            if (!recoverWithSmallerTile(session, error)) fail(minecraft, session, "Tile render failed", error);
        } finally {
            cameraState.projectionMatrix.set(oldProjection);
            guiState.isHudHidden = oldHudHidden;
            windowState.width = oldWidth;
            windowState.height = oldHeight;
            ((GameRendererAccessor) renderer).hypershot$setRenderBlockOutline(oldBlockOutline);
            ((GameRendererAccessor) renderer).hypershot$setMainRenderTarget(originalTarget);
            try {
                renderer.resize(originalTarget.width, originalTarget.height);
                renderer.extract(((MinecraftAccessor) minecraft).hypershot$getDeltaTracker(), advanceGameTime);
            } catch (Throwable restoreError) {
                logger.error("Unable to restore Minecraft renderer state after HyperShot pass", restoreError);
            }
            inCapturePass.set(false);
        }
    }

    public boolean isRenderingCapturePass() { return inCapturePass.get(); }

    public boolean shouldHideHand() {
        CaptureSession session = active;
        return inCapturePass.get() && session != null && session.request.hideHand();
    }

    private void ensureCaptureTarget(int width, int height) {
        RenderSystem.assertOnRenderThread();
        if (captureTarget == null) captureTarget = new MainTarget(width, height);
        if (captureTarget.width != width || captureTarget.height != height) captureTarget.resize(width, height);
        if (captureTarget.width != width || captureTarget.height != height) {
            throw new IllegalStateException("Backend could not allocate requested tile target " + width + "x" + height);
        }
    }


    private synchronized boolean recoverWithSmallerTile(CaptureSession session, Throwable error) {
        if (active != session || session.nextTileIndex != 0 || session.layout.tileSize() <= 512) return false;
        String type = error.getClass().getName().toLowerCase(Locale.ROOT);
        String message = String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
        if (!type.contains("memory") && !type.contains("allocation") && !message.contains("allocate") && !message.contains("out of memory")) return false;
        int reduced = Math.max(512, session.layout.tileSize() / 2);
        int overlap = Math.min(session.request.overlap(), Math.max(0, reduced / 2 - 1));
        session.layout = TileLayout.create(session.request.resolution().width(), session.request.resolution().height(), reduced, overlap);
        session.recovery = dev.hypershot.core.RecoveryManifest.create(session.id, session.spoolPath, session.layout.columns(), session.layout.rows());
        session.progress = new dev.hypershot.core.CaptureProgress(session.layout.tiles().size(), session.layout.totalPixels());
        session.progress.begin(CapturePhase.CAPTURING_TILES, System.nanoTime());
        try { AtomicJson.write(session.recoveryPath, session.recovery.toJson()); }
        catch (IOException manifestError) { error.addSuppressed(manifestError); return false; }
        logger.warn("HyperShot reduced tile size to {} after backend allocation failure", reduced, error);
        listener.onProgress(session.id, session.progress.snapshot(System.nanoTime()));
        return true;
    }

    private void applyTileProjection(CaptureSession session, Tile tile, CameraRenderState cameraState) {
        double near = 0.05;
        double far = Math.max(near + 1.0, cameraState.depthFar);
        float fov = Minecraft.getInstance().gameRenderer.mainCamera().getFov();
        ProjectionWindow projection = TileProjectionCalculator.window(tile, session.layout.width(), session.layout.height(), fov, near, far);
        boolean zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        cameraState.projectionMatrix.setFrustum(
                (float) projection.left(), (float) projection.right(),
                (float) projection.bottom(), (float) projection.top(),
                (float) projection.far(), (float) projection.near(), zZeroToOne
        );
    }

    private void acceptTileReadback(Minecraft minecraft, CaptureSession session, Tile tile, NativeImage image) {
        try (image) {
            if (session != active || session.cancellation.isCancelled()) {
                minecraft.execute(() -> finishCancelled(minecraft, session));
                return;
            }
            int[] argb = image.getPixels();
            byte[] rgba = new byte[CheckedMath.checkedInt(CheckedMath.bytesForPixels(image.getWidth(), image.getHeight(), 4), "tile readback")];
            for (int i = 0, p = 0; i < argb.length; i++) {
                int pixel = argb[i];
                rgba[p++] = (byte) (pixel >> 16);
                rgba[p++] = (byte) (pixel >> 8);
                rgba[p++] = (byte) pixel;
                rgba[p++] = (byte) (pixel >> 24);
            }
            ioExecutor.execute(() -> writeTile(minecraft, session, tile, rgba));
        } catch (Throwable error) {
            fail(minecraft, session, "GPU readback failed", error);
        }
    }

    private void writeTile(Minecraft minecraft, CaptureSession session, Tile tile, byte[] rgba) {
        try {
            session.cancellation.throwIfCancelled();
            session.surface.writeTile(tile, rgba);
            session.recovery = session.recovery.withCompleted(tile.index());
            AtomicJson.write(session.recoveryPath, session.recovery.toJson());
            session.progress.tileCompleted((long) tile.contentWidth() * tile.contentHeight(), System.nanoTime());
            minecraft.execute(() -> {
                if (active != session) return;
                session.nextTileIndex++;
                session.waitingForReadback = false;
                listener.onProgress(session.id, session.progress.snapshot(System.nanoTime()));
                if (!session.hasMoreTiles()) beginFinalize(minecraft, session);
            });
        } catch (Throwable error) {
            if (session.cancellation.isCancelled()) minecraft.execute(() -> finishCancelled(minecraft, session));
            else fail(minecraft, session, "Writing tile failed", error);
        }
    }

    private synchronized void beginFinalize(Minecraft minecraft, CaptureSession session) {
        if (active != session || session.finalizing) return;
        session.finalizing = true;
        session.progress.setPhase(CapturePhase.GENERATING_THUMBNAIL);
        listener.onProgress(session.id, session.progress.snapshot(System.nanoTime()));
        ioExecutor.execute(() -> {
            try {
                session.cancellation.throwIfCancelled();
                session.surface.force();
                Files.deleteIfExists(session.thumbnailPath);
                ThumbnailGenerator.writePng(session.surface, session.layout.width(), session.layout.height(),
                        session.thumbnailPath, 480, 270, 4);
                session.progress.setPhase(CapturePhase.ENCODING);
                listener.onProgress(session.id, session.progress.snapshot(System.nanoTime()));
                Files.deleteIfExists(session.temporaryImage);
                session.encoder.encode(session.surface, session.layout.width(), session.layout.height(), session.temporaryImage, session.encoderOptions);
                session.close();
                try {
                    Files.move(session.temporaryImage, session.finalImage, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(session.temporaryImage, session.finalImage);
                }
                long fileSize = Files.size(session.finalImage);
                if (session.request.includeMetadata()) {
                    session.progress.setPhase(CapturePhase.WRITING_METADATA);
                    listener.onProgress(session.id, session.progress.snapshot(System.nanoTime()));
                    writeMetadata(minecraft, session, fileSize);
                }
                Files.deleteIfExists(session.spoolPath);
                Files.deleteIfExists(session.recoveryPath);
                minecraft.execute(() -> complete(session, fileSize));
            } catch (Throwable error) {
                if (session.cancellation.isCancelled()) minecraft.execute(() -> finishCancelled(minecraft, session));
                else fail(minecraft, session, "Final encoding failed", error);
            }
        });
    }

    private void writeMetadata(Minecraft minecraft, CaptureSession session, long fileSize) throws IOException {
        var player = minecraft.player;
        boolean privateMode = privacy == MetadataPrivacy.PRIVATE;
        CaptureMetadata metadata = new CaptureMetadata(
                1,
                session.id,
                OffsetDateTime.now(ZoneId.systemDefault()),
                session.finalImage.getFileName().toString(),
                session.layout.width(),
                session.layout.height(),
                session.request.outputFormat().name(),
                fileSize,
                Duration.between(session.startedAt, java.time.Instant.now()).toMillis(),
                session.layout.tileSize(),
                session.layout.tileSize(),
                session.layout.tiles().size(),
                session.request.mode().name(),
                session.request.presetName(),
                SharedConstants.getCurrentVersion().name(),
                System.getProperty("java.version"),
                System.getProperty("os.name") + " " + System.getProperty("os.version"),
                capabilityScanner.backendName(),
                capabilityScanner.gpuName(),
                capabilityScanner.driverInfo(),
                null,
                null,
                privateMode || minecraft.level == null ? null : minecraft.level.dimension().identifier().toString(),
                privateMode || player == null ? null : player.getX(),
                privateMode || player == null ? null : player.getY(),
                privateMode || player == null ? null : player.getZ(),
                player == null ? null : player.getYRot(),
                player == null ? null : player.getXRot(),
                minecraft.gameRenderer.mainCamera().getFov(),
                session.request.hideHud(),
                session.request.hideHand(),
                session.request.hideBlockOutline(),
                List.of()
        );
        metadataWriter.write(session.metadataPath, metadata);
    }

    private synchronized void complete(CaptureSession session, long fileSize) {
        if (active != session) return;
        session.progress.setPhase(CapturePhase.COMPLETE);
        active = null;
        listener.onCompleted(session.id, session.finalImage, session.metadataPath, session.thumbnailPath, fileSize);
    }

    private void fail(Minecraft minecraft, CaptureSession session, String message, Throwable error) {
        logger.error(message, error);
        minecraft.execute(() -> {
            synchronized (CaptureManager.this) {
                if (active != session) return;
                active = null;
            }
            cleanupFiles(session, false);
            listener.onFailed(session.id, message + ": " + error.getMessage(), error);
        });
    }

    private synchronized void finishCancelled(Minecraft minecraft, CaptureSession session) {
        if (active != session) return;
        active = null;
        cleanupFiles(session, false);
        listener.onCancelled(session.id, session.cancellation.reason());
    }

    private void cleanupFiles(CaptureSession session, boolean preserveRecovery) {
        try { session.close(); } catch (Exception ignored) {}
        try { Files.deleteIfExists(session.temporaryImage); } catch (IOException ignored) {}
        try { Files.deleteIfExists(session.thumbnailPath); } catch (IOException ignored) {}
        if (!preserveRecovery) {
            try { Files.deleteIfExists(session.spoolPath); } catch (IOException ignored) {}
            try { Files.deleteIfExists(session.recoveryPath); } catch (IOException ignored) {}
        }
    }

    private static String humanBytes(long bytes) {
        double value = bytes;
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++; }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    @Override
    public void close() {
        CaptureSession session = active;
        if (session != null) session.cancellation.cancel("Minecraft is shutting down");
        ioExecutor.shutdown();
        if (captureTarget != null) {
            RenderSystem.assertOnRenderThread();
            captureTarget.destroyBuffers();
            captureTarget = null;
        }
    }
}
