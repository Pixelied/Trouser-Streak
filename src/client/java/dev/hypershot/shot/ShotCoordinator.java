package dev.hypershot.shot;

import dev.hypershot.capture.CaptureListener;
import dev.hypershot.capture.CaptureManager;
import dev.hypershot.capture.CaptureRequest;
import dev.hypershot.config.HyperShotConfig;
import dev.hypershot.core.CapturePhase;
import dev.hypershot.core.CapturePreflight;
import dev.hypershot.core.CaptureProgressSnapshot;
import dev.hypershot.core.CaptureSafetyState;
import dev.hypershot.core.camera.ShotPreparationMachine;
import dev.hypershot.core.camera.ShotReadiness;
import dev.hypershot.core.camera.ShotReadinessState;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public final class ShotCoordinator implements CaptureListener {
    private static final long TERMINAL_DISPLAY_NANOS = 2_000_000_000L;

    private final CaptureManager captureManager;
    private final HyperShotConfig config;
    private final ShotPreparationMachine preparation = new ShotPreparationMachine();

    private CaptureRequest pendingRequest;
    private CapturePreflight pendingPreflight;
    private String activeCaptureId;
    private CaptureProgressSnapshot activeProgress;
    private boolean startRequested;
    private ShotReadiness terminalReadiness;
    private long terminalUntilNanos;

    public ShotCoordinator(CaptureManager captureManager, HyperShotConfig config) {
        this.captureManager = Objects.requireNonNull(captureManager);
        this.config = Objects.requireNonNull(config);
    }

    public void queuePhoto(Minecraft minecraft) {
        Objects.requireNonNull(minecraft);
        long nowNanos = System.nanoTime();
        if (minecraft.level == null) {
            setTerminal(ShotReadinessState.BLOCKED, "Enter a world before taking a photo", nowNanos);
            return;
        }
        if (pendingRequest != null || captureManager.isActive()) {
            setTerminal(ShotReadinessState.BLOCKED, "A HyperShot capture is already active", nowNanos);
            return;
        }

        var target = minecraft.gameRenderer.mainRenderTarget();
        CaptureRequest request = CaptureRequest.from(config.activePreset(), target.width, target.height);
        final CapturePreflight preflight;
        try {
            preflight = captureManager.preflight(request);
        } catch (IOException | RuntimeException error) {
            setTerminal(ShotReadinessState.BLOCKED, "Preflight failed: " + safeMessage(error), nowNanos);
            return;
        }
        if (preflight.safetyState() == CaptureSafetyState.CANNOT_START) {
            setTerminal(ShotReadinessState.BLOCKED, preflight.reason(), nowNanos);
            return;
        }
        if (preflight.safetyState() == CaptureSafetyState.NOT_RECOMMENDED) {
            setTerminal(ShotReadinessState.BLOCKED, preflight.reason() + " — confirm from Advanced settings", nowNanos);
            return;
        }

        pendingRequest = request;
        pendingPreflight = preflight;
        activeCaptureId = null;
        activeProgress = null;
        startRequested = false;
        terminalReadiness = null;
        long timerNanos = Math.multiplyExact((long) config.timerSeconds, 1_000_000_000L);
        long settleNanos = Math.multiplyExact(config.shaderSettleProfile.resolveMillis(config.customShaderSettleMs), 1_000_000L);
        preparation.start(nowNanos, timerNanos, settleNanos);
    }

    public void tick(Minecraft minecraft, long nowNanos) {
        Objects.requireNonNull(minecraft);
        if (terminalReadiness != null && nowNanos >= terminalUntilNanos) {
            terminalReadiness = null;
        }
        if (pendingRequest == null || activeCaptureId != null || startRequested) return;
        preparation.tick(nowNanos);
        if (preparation.readiness(nowNanos).state() != ShotReadinessState.READY) return;
        startRequested = true;
        captureManager.start(minecraft, pendingRequest);
    }

    public void cancel(String reason) {
        long nowNanos = System.nanoTime();
        if (captureManager.isActive()) {
            captureManager.cancel(reason == null ? "Camera shot cancelled" : reason);
            return;
        }
        if (pendingRequest != null) {
            preparation.cancel();
            clearActive();
            setTerminal(ShotReadinessState.CANCELLED, reason == null ? "Shot cancelled" : reason, nowNanos);
        }
    }

    public boolean hasQueuedShot() {
        return pendingRequest != null || activeCaptureId != null;
    }

    public ShotReadiness readiness(long nowNanos) {
        if (terminalReadiness != null && nowNanos < terminalUntilNanos) return terminalReadiness;
        if (activeCaptureId != null) {
            if (activeProgress != null && isFinalizing(activeProgress.phase())) {
                return new ShotReadiness(ShotReadinessState.FINALIZING, "Finishing image", 0L);
            }
            return new ShotReadiness(ShotReadinessState.CAPTURING, "Capturing image", 0L);
        }
        if (pendingRequest != null) {
            ShotReadiness prep = preparation.readiness(nowNanos);
            if (prep.state() == ShotReadinessState.READY && pendingPreflight != null
                    && pendingPreflight.safetyState() == CaptureSafetyState.HIGH_LOAD) {
                return new ShotReadiness(ShotReadinessState.HIGH_LOAD, pendingPreflight.reason(), 0L);
            }
            return prep;
        }
        return new ShotReadiness(ShotReadinessState.READY, "Ready", 0L);
    }

    public void noteSceneChanged(long nowNanos) {
        if (pendingRequest != null && activeCaptureId == null) preparation.noteSceneChanged(nowNanos);
    }

    public CapturePreflight preflight() {
        return pendingPreflight;
    }

    @Override
    public void onStarted(String captureId, CaptureRequest request) {
        if (pendingRequest != null && pendingRequest.equals(request)) {
            activeCaptureId = captureId;
            activeProgress = null;
        }
    }

    @Override
    public void onProgress(String captureId, CaptureProgressSnapshot progress) {
        if (captureId.equals(activeCaptureId)) activeProgress = progress;
    }

    @Override
    public void onCompleted(String captureId, Path image, Path metadata, Path thumbnail, long fileSize) {
        if (!captureId.equals(activeCaptureId)) return;
        clearActive();
        terminalReadiness = null;
    }

    @Override
    public void onCancelled(String captureId, String reason) {
        if (!captureId.equals(activeCaptureId)) return;
        clearActive();
        setTerminal(ShotReadinessState.CANCELLED, reason == null ? "Shot cancelled" : reason, System.nanoTime());
    }

    @Override
    public void onFailed(String captureId, String message, Throwable error) {
        if (!("not-started".equals(captureId) && startRequested) && !captureId.equals(activeCaptureId)) return;
        clearActive();
        String detail = message == null || message.isBlank() ? safeMessage(error) : message;
        setTerminal(ShotReadinessState.FAILED, detail, System.nanoTime());
    }

    private void clearActive() {
        pendingRequest = null;
        pendingPreflight = null;
        activeCaptureId = null;
        activeProgress = null;
        startRequested = false;
    }

    private void setTerminal(ShotReadinessState state, String reason, long nowNanos) {
        terminalReadiness = new ShotReadiness(state, reason, 0L);
        terminalUntilNanos = saturatingAdd(nowNanos, TERMINAL_DISPLAY_NANOS);
    }

    private static boolean isFinalizing(CapturePhase phase) {
        return phase != null && phase != CapturePhase.CAPTURING_TILES && phase != CapturePhase.PAUSED;
    }

    private static String safeMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) return "Unknown error";
        return error.getMessage();
    }

    private static long saturatingAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
