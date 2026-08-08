package dev.hypershot.shot;

import dev.hypershot.capture.CaptureListener;
import dev.hypershot.capture.CaptureManager;
import dev.hypershot.capture.CaptureRequest;
import dev.hypershot.config.HyperShotConfig;
import dev.hypershot.core.CapturePhase;
import dev.hypershot.core.CapturePreflight;
import dev.hypershot.core.CaptureProgressSnapshot;
import dev.hypershot.core.CaptureSafetyState;
import dev.hypershot.core.camera.BurstPlan;
import dev.hypershot.core.camera.CameraMode;
import dev.hypershot.core.camera.CameraSceneSignature;
import dev.hypershot.core.camera.PreparationContinuation;
import dev.hypershot.core.camera.SequencePreflight;
import dev.hypershot.core.camera.SequencePreflightCalculator;
import dev.hypershot.core.camera.ShotPreparationMachine;
import dev.hypershot.core.camera.ShotReadiness;
import dev.hypershot.core.camera.ShotReadinessState;
import dev.hypershot.core.camera.TimeBracketPlan;
import dev.hypershot.gallery.CaptureGroupAnnotator;
import dev.hypershot.gallery.CaptureGroupContext;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** High-level photography orchestrator. CaptureManager remains responsible for exactly one rendered/encoded image. */
public final class ShotCoordinator implements CaptureListener {
    private static final long TERMINAL_DISPLAY_NANOS = 2_000_000_000L;

    private final CaptureManager captureManager;
    private final HyperShotConfig config;
    private final CaptureGroupContext groupContext;
    private final CaptureGroupAnnotator groupAnnotator;
    private final TimeSceneController timeController;
    private final ShotPreparationMachine preparation = new ShotPreparationMachine();

    private Session session;
    private Phase phase = Phase.IDLE;
    private CaptureProgressSnapshot activeProgress;
    private String activeCaptureId;
    private boolean startRequested;
    private CameraSceneSignature settleSignature;
    private boolean currentTimeApplied;
    private long nextFrameNotBeforeNanos;
    private ShotReadiness terminalReadiness;
    private long terminalUntilNanos;

    public ShotCoordinator(CaptureManager captureManager, HyperShotConfig config,
                           CaptureGroupContext groupContext, CaptureGroupAnnotator groupAnnotator,
                           TimeSceneController timeController) {
        this.captureManager = Objects.requireNonNull(captureManager);
        this.config = Objects.requireNonNull(config);
        this.groupContext = Objects.requireNonNull(groupContext);
        this.groupAnnotator = Objects.requireNonNull(groupAnnotator);
        this.timeController = Objects.requireNonNull(timeController);
    }

    public void queueActiveMode(Minecraft minecraft) {
        switch (config.cameraMode) {
            case PHOTO -> queuePhoto(minecraft);
            case BURST -> queueBurst(minecraft);
            case TIME -> queueTimeBracket(minecraft);
            case CINEMATIC -> setTerminal(ShotReadinessState.BLOCKED, "Cinematic scene controls are not enabled in this build yet", System.nanoTime());
        }
    }

    /** Fast F2 always remains a single Photo even if the viewfinder was last left in Burst/Time mode. */
    public void queuePhoto(Minecraft minecraft) {
        queue(minecraft, Session.photo(resolveBase(minecraft)));
    }

    public void queueBurst(Minecraft minecraft) {
        Base base = resolveBase(minecraft);
        if (base == null) return;
        BurstPlan plan;
        try {
            plan = new BurstPlan(config.burstFrameCount, config.burstIntervalMs);
        } catch (IllegalArgumentException error) {
            setTerminal(ShotReadinessState.BLOCKED, error.getMessage(), System.nanoTime());
            return;
        }
        List<Frame> frames = new ArrayList<>(plan.frameCount());
        for (int i = 1; i <= plan.frameCount(); i++) frames.add(new Frame("Burst " + i + "/" + plan.frameCount(), null));
        SequencePreflight sequence = SequencePreflightCalculator.calculate(base.preflight, plan.frameCount());
        queue(minecraft, Session.sequence(CameraMode.BURST, base, "BURST", frames,
                Math.multiplyExact(plan.intervalMillis(), 1_000_000L), sequence));
    }

    public void queueTimeBracket(Minecraft minecraft) {
        Base base = resolveBase(minecraft);
        if (base == null) return;
        if (!timeController.available(minecraft)) {
            setTerminal(ShotReadinessState.BLOCKED, "Time mode is singleplayer only — the server controls world time", System.nanoTime());
            return;
        }
        final TimeBracketPlan plan;
        try {
            plan = config.timeUseCuratedSequence
                    ? TimeBracketPlan.curated()
                    : TimeBracketPlan.custom(config.timeStartTick, config.timeEndTick, config.timeStepTicks, config.timeWrapDayBoundary);
        } catch (IllegalArgumentException error) {
            setTerminal(ShotReadinessState.BLOCKED, "Invalid time bracket: " + error.getMessage(), System.nanoTime());
            return;
        }
        List<Frame> frames = plan.frames().stream().map(frame -> new Frame(frame.label(), frame.dayTime())).toList();
        SequencePreflight sequence = SequencePreflightCalculator.calculate(base.preflight, frames.size());
        queue(minecraft, Session.sequence(CameraMode.TIME, base, "TIME_BRACKET", frames, 0L, sequence));
    }

    private Base resolveBase(Minecraft minecraft) {
        Objects.requireNonNull(minecraft);
        long nowNanos = System.nanoTime();
        if (minecraft.level == null || minecraft.player == null) {
            setTerminal(ShotReadinessState.BLOCKED, "Enter a world before taking a HyperShot photo", nowNanos);
            return null;
        }
        if (session != null || captureManager.isActive()) {
            setTerminal(ShotReadinessState.BLOCKED, "A HyperShot shot is already active", nowNanos);
            return null;
        }
        var target = minecraft.gameRenderer.mainRenderTarget();
        CaptureRequest request = CaptureRequest.from(config.activePreset(), target.width, target.height);
        try {
            CapturePreflight preflight = captureManager.preflight(request);
            if (preflight.safetyState() == CaptureSafetyState.CANNOT_START) {
                setTerminal(ShotReadinessState.BLOCKED, preflight.reason(), nowNanos);
                return null;
            }
            if (preflight.safetyState() == CaptureSafetyState.NOT_RECOMMENDED) {
                setTerminal(ShotReadinessState.BLOCKED, preflight.reason() + " — use a safer preset or confirm from Advanced settings", nowNanos);
                return null;
            }
            return new Base(request, preflight);
        } catch (IOException | RuntimeException error) {
            setTerminal(ShotReadinessState.BLOCKED, "Preflight failed: " + safeMessage(error), nowNanos);
            return null;
        }
    }

    private void queue(Minecraft minecraft, Session newSession) {
        if (newSession == null) return;
        long nowNanos = System.nanoTime();
        if (newSession.sequencePreflight != null) {
            if (newSession.sequencePreflight.safetyState() == CaptureSafetyState.CANNOT_START) {
                setTerminal(ShotReadinessState.BLOCKED, newSession.sequencePreflight.reason(), nowNanos);
                return;
            }
            if (newSession.sequencePreflight.safetyState() == CaptureSafetyState.NOT_RECOMMENDED) {
                setTerminal(ShotReadinessState.BLOCKED, newSession.sequencePreflight.reason() + " — reduce the sequence or resolution", nowNanos);
                return;
            }
        }
        if (newSession.mode == CameraMode.TIME && !timeController.begin(minecraft)) {
            setTerminal(ShotReadinessState.BLOCKED, "Time mode could not access the integrated singleplayer server", nowNanos);
            return;
        }
        session = newSession;
        phase = Phase.INITIAL_PREPARATION;
        activeProgress = null;
        activeCaptureId = null;
        startRequested = false;
        settleSignature = null;
        currentTimeApplied = false;
        terminalReadiness = null;
        long timerNanos = Math.multiplyExact((long) config.timerSeconds, 1_000_000_000L);
        long settleNanos = newSession.mode == CameraMode.TIME ? 0L : settleNanos();
        preparation.start(nowNanos, timerNanos, settleNanos);
    }

    public void tick(Minecraft minecraft, long nowNanos) {
        Objects.requireNonNull(minecraft);
        if (terminalReadiness != null && nowNanos >= terminalUntilNanos) terminalReadiness = null;
        if (timeController.active()) timeController.tick(minecraft, nowNanos);
        if (session == null) return;
        if (session.mode == CameraMode.TIME && timeController.failed()) {
            failSession("Time control failed: " + timeController.errorMessage(), null);
            return;
        }
        if (activeCaptureId != null || startRequested) return;

        switch (phase) {
            case INITIAL_PREPARATION, FRAME_SETTLE -> tickPreparation(minecraft, nowNanos);
            case APPLY_TIME -> tickApplyTime(minecraft, nowNanos);
            case WAIT_INTERVAL -> {
                if (nowNanos >= nextFrameNotBeforeNanos) startCurrentFrame(minecraft);
            }
            case IDLE, CAPTURING -> { }
        }
    }

    private void tickPreparation(Minecraft minecraft, long nowNanos) {
        preparation.tick(nowNanos);
        ShotReadiness readiness = preparation.readiness(nowNanos);
        if (readiness.state() == ShotReadinessState.SETTLING_SHADERS && minecraft.player != null) {
            CameraSceneSignature current = signature(minecraft);
            if (settleSignature == null) settleSignature = current;
            else if (settleSignature.meaningfullyDiffers(current)) {
                preparation.noteSceneChanged(nowNanos);
                settleSignature = current;
            }
            readiness = preparation.readiness(nowNanos);
        } else if (readiness.state() != ShotReadinessState.SETTLING_SHADERS) {
            settleSignature = null;
        }
        if (readiness.state() != ShotReadinessState.READY) return;

        boolean postSceneSettle = phase == Phase.FRAME_SETTLE;
        PreparationContinuation.Action continuation = PreparationContinuation.next(session.mode, postSceneSettle);
        if (continuation == PreparationContinuation.Action.APPLY_TIME) {
            phase = Phase.APPLY_TIME;
            currentTimeApplied = false;
        } else {
            startCurrentFrame(minecraft);
        }
    }

    private void tickApplyTime(Minecraft minecraft, long nowNanos) {
        if (!timeController.snapshotReady()) return;
        Frame frame = session.currentFrame();
        if (frame.dayTime == null) {
            failSession("Time frame has no world time", null);
            return;
        }
        if (!currentTimeApplied) {
            try {
                timeController.applyTime(frame.dayTime);
                currentTimeApplied = true;
            } catch (RuntimeException error) {
                failSession("Unable to apply time state", error);
                return;
            }
            return;
        }
        if (!timeController.clientObservedTarget(minecraft)) return;
        currentTimeApplied = false;
        if (config.timeSettleBetweenFrames && settleNanos() > 0) {
            preparation.start(nowNanos, 0L, settleNanos());
            settleSignature = null;
            phase = Phase.FRAME_SETTLE;
        } else {
            startCurrentFrame(minecraft);
        }
    }

    private void startCurrentFrame(Minecraft minecraft) {
        if (session == null || startRequested || activeCaptureId != null) return;
        startRequested = true;
        phase = Phase.CAPTURING;
        captureManager.start(minecraft, session.base.request);
    }

    public void cancel(String reason) {
        long nowNanos = System.nanoTime();
        if (captureManager.isActive()) {
            captureManager.cancel(reason == null ? "Camera shot cancelled" : reason);
            return;
        }
        if (session != null) {
            preparation.cancel();
            String groupId = session.groupId;
            restoreScene();
            clearSession();
            if (groupId != null) groupAnnotator.markIncomplete(groupId);
            setTerminal(ShotReadinessState.CANCELLED, reason == null ? "Shot cancelled" : reason, nowNanos);
        }
    }

    public boolean hasQueuedShot() {
        return session != null || activeCaptureId != null;
    }

    public ShotReadiness readiness(long nowNanos) {
        if (terminalReadiness != null && nowNanos < terminalUntilNanos) return terminalReadiness;
        if (activeCaptureId != null || (session != null && phase == Phase.CAPTURING)) {
            String frame = session == null ? "" : session.frameStatus();
            if (activeProgress != null && isFinalizing(activeProgress.phase())) {
                return new ShotReadiness(ShotReadinessState.FINALIZING, "Finishing " + frame, 0L);
            }
            return new ShotReadiness(ShotReadinessState.CAPTURING, "Capturing " + frame, 0L);
        }
        if (session == null) return new ShotReadiness(ShotReadinessState.READY, "Ready", 0L);
        return switch (phase) {
            case INITIAL_PREPARATION, FRAME_SETTLE -> {
                ShotReadiness prep = preparation.readiness(nowNanos);
                if (prep.state() == ShotReadinessState.READY && session.sequencePreflight != null
                        && session.sequencePreflight.safetyState() == CaptureSafetyState.HIGH_LOAD) {
                    yield new ShotReadiness(ShotReadinessState.HIGH_LOAD, session.sequencePreflight.reason(), 0L);
                }
                yield prep;
            }
            case APPLY_TIME -> new ShotReadiness(ShotReadinessState.PREPARING_SCENE,
                    "Preparing " + session.currentFrame().label, 0L);
            case WAIT_INTERVAL -> new ShotReadiness(ShotReadinessState.WAITING_FOR_INTERVAL,
                    "Next frame " + session.frameStatus(), Math.max(0L, nextFrameNotBeforeNanos - nowNanos));
            case CAPTURING -> new ShotReadiness(ShotReadinessState.CAPTURING, "Capturing " + session.frameStatus(), 0L);
            case IDLE -> new ShotReadiness(ShotReadinessState.READY, "Ready", 0L);
        };
    }

    public void noteSceneChanged(long nowNanos) {
        if (session != null && activeCaptureId == null) preparation.noteSceneChanged(nowNanos);
    }

    public CapturePreflight preflight() {
        return session == null ? null : session.base.preflight;
    }

    public SequencePreflight sequencePreflight() {
        return session == null ? null : session.sequencePreflight;
    }

    public CameraMode sessionMode() {
        return session == null ? null : session.mode;
    }

    public String sessionStatus() {
        return session == null ? "" : session.frameStatus();
    }

    public boolean timeModeAvailable(Minecraft minecraft) {
        return timeController.available(minecraft);
    }

    @Override
    public void onStarted(String captureId, CaptureRequest request) {
        if (session == null || !session.base.request.equals(request)) return;
        activeCaptureId = captureId;
        activeProgress = null;
        startRequested = false;
        if (session.groupId != null) {
            Frame frame = session.currentFrame();
            groupContext.attach(captureId, new CaptureGroupContext.GroupInfo(
                    session.groupId, session.groupType, session.index + 1, session.frames.size(), frame.label));
        }
    }

    @Override
    public void onProgress(String captureId, CaptureProgressSnapshot progress) {
        if (captureId.equals(activeCaptureId)) activeProgress = progress;
    }

    @Override
    public void onCompleted(String captureId, Path image, Path metadata, Path thumbnail, long fileSize) {
        if (!captureId.equals(activeCaptureId) || session == null) return;
        activeCaptureId = null;
        activeProgress = null;
        startRequested = false;
        if (session.index + 1 >= session.frames.size()) {
            restoreScene();
            clearSession();
            terminalReadiness = null;
            return;
        }
        session.index++;
        if (session.mode == CameraMode.BURST) {
            phase = Phase.WAIT_INTERVAL;
            nextFrameNotBeforeNanos = saturatingAdd(System.nanoTime(), session.intervalNanos);
        } else if (session.mode == CameraMode.TIME) {
            phase = Phase.APPLY_TIME;
            currentTimeApplied = false;
        } else {
            clearSession();
        }
    }

    @Override
    public void onCancelled(String captureId, String reason) {
        if (!captureId.equals(activeCaptureId)) return;
        String groupId = session == null ? null : session.groupId;
        restoreScene();
        clearSession();
        if (groupId != null) groupAnnotator.markIncomplete(groupId);
        setTerminal(ShotReadinessState.CANCELLED, reason == null ? "Shot cancelled" : reason, System.nanoTime());
    }

    @Override
    public void onFailed(String captureId, String message, Throwable error) {
        if (!("not-started".equals(captureId) && startRequested) && !captureId.equals(activeCaptureId)) return;
        String detail = message == null || message.isBlank() ? safeMessage(error) : message;
        failSession(detail, error);
    }

    private void failSession(String detail, Throwable error) {
        String groupId = session == null ? null : session.groupId;
        restoreScene();
        clearSession();
        if (groupId != null) groupAnnotator.markIncomplete(groupId);
        setTerminal(ShotReadinessState.FAILED, error == null ? detail : detail + ": " + safeMessage(error), System.nanoTime());
    }

    private CameraSceneSignature signature(Minecraft minecraft) {
        float fov = minecraft.gameRenderer.mainCamera().getFov();
        return new CameraSceneSignature(
                minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(),
                minecraft.player.getYRot(), minecraft.player.getXRot(), fov);
    }

    private long settleNanos() {
        return Math.multiplyExact(config.shaderSettleProfile.resolveMillis(config.customShaderSettleMs), 1_000_000L);
    }

    private void restoreScene() {
        if (timeController.active()) timeController.restore();
    }

    private void clearSession() {
        session = null;
        phase = Phase.IDLE;
        activeCaptureId = null;
        activeProgress = null;
        startRequested = false;
        settleSignature = null;
        currentTimeApplied = false;
        nextFrameNotBeforeNanos = 0L;
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

    private enum Phase { IDLE, INITIAL_PREPARATION, APPLY_TIME, FRAME_SETTLE, WAIT_INTERVAL, CAPTURING }

    private record Base(CaptureRequest request, CapturePreflight preflight) { }

    private record Frame(String label, Long dayTime) {
        private Frame {
            Objects.requireNonNull(label);
        }
    }

    private static final class Session {
        final CameraMode mode;
        final Base base;
        final String groupId;
        final String groupType;
        final List<Frame> frames;
        final long intervalNanos;
        final SequencePreflight sequencePreflight;
        int index;

        private Session(CameraMode mode, Base base, String groupId, String groupType, List<Frame> frames,
                        long intervalNanos, SequencePreflight sequencePreflight) {
            this.mode = mode;
            this.base = base;
            this.groupId = groupId;
            this.groupType = groupType;
            this.frames = List.copyOf(frames);
            this.intervalNanos = intervalNanos;
            this.sequencePreflight = sequencePreflight;
        }

        static Session photo(Base base) {
            if (base == null) return null;
            return new Session(CameraMode.PHOTO, base, null, null, List.of(new Frame("Photo", null)), 0L, null);
        }

        static Session sequence(CameraMode mode, Base base, String groupType, List<Frame> frames,
                                long intervalNanos, SequencePreflight preflight) {
            if (base == null) return null;
            return new Session(mode, base, UUID.randomUUID().toString(), groupType, frames, intervalNanos, preflight);
        }

        Frame currentFrame() { return frames.get(index); }
        String frameStatus() { return frames.size() == 1 ? currentFrame().label : currentFrame().label + " (" + (index + 1) + "/" + frames.size() + ")"; }
    }
}
