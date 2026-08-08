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
import dev.hypershot.core.camera.ChunkReadiness;
import dev.hypershot.core.camera.CinematicCapabilities;
import dev.hypershot.core.camera.CinematicFlowMachine;
import dev.hypershot.core.camera.CinematicOptions;
import dev.hypershot.core.camera.CinematicTimePreset;
import dev.hypershot.core.camera.CinematicWeatherPreset;
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
    private final CinematicSceneController cinematicController;
    private final CameraLockController cameraLockController;
    private final ChunkReadinessProbe chunkReadinessProbe = new ChunkReadinessProbe(2);
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

    private CinematicFlowMachine cinematicFlow;
    private boolean cinematicSceneControllerUsed;
    private boolean cinematicSceneApplyRequested;
    private boolean cinematicFreezeRequested;
    private boolean cinematicSettleStarted;
    private ChunkReadiness cinematicChunkReadiness;
    private boolean cinematicChunkTimedOut;
    private long cinematicChunkDeadlineNanos;
    private ShotReadinessState cinematicTerminalState;
    private String cinematicTerminalReason;

    public ShotCoordinator(CaptureManager captureManager, HyperShotConfig config,
                           CaptureGroupContext groupContext, CaptureGroupAnnotator groupAnnotator,
                           TimeSceneController timeController, CinematicSceneController cinematicController,
                           CameraLockController cameraLockController) {
        this.captureManager = Objects.requireNonNull(captureManager);
        this.config = Objects.requireNonNull(config);
        this.groupContext = Objects.requireNonNull(groupContext);
        this.groupAnnotator = Objects.requireNonNull(groupAnnotator);
        this.timeController = Objects.requireNonNull(timeController);
        this.cinematicController = Objects.requireNonNull(cinematicController);
        this.cameraLockController = Objects.requireNonNull(cameraLockController);
    }

    public void queueActiveMode(Minecraft minecraft) {
        switch (config.cameraMode) {
            case PHOTO -> queuePhoto(minecraft);
            case BURST -> queueBurst(minecraft);
            case TIME -> queueTimeBracket(minecraft);
            case CINEMATIC -> queueCinematic(minecraft);
        }
    }

    /** Fast F2 always remains a single Photo even if the viewfinder was last left in another camera mode. */
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

    public void queueCinematic(Minecraft minecraft) {
        Base base = resolveBase(minecraft);
        if (base == null) return;
        final CinematicOptions requested;
        try {
            requested = new CinematicOptions(
                    config.cinematicWorldFreeze,
                    config.cinematicTimePreset,
                    config.cinematicWeatherPreset,
                    config.cinematicWaitForChunks,
                    config.cinematicChunkTimeoutMs,
                    config.cinematicCameraLock,
                    config.cinematicFovLock,
                    config.cinematicCleanFrame);
        } catch (IllegalArgumentException error) {
            setTerminal(ShotReadinessState.BLOCKED, "Invalid Cinematic settings: " + error.getMessage(), System.nanoTime());
            return;
        }
        CinematicOptions options = requested.gatedBy(cinematicController.capabilities(minecraft));
        if (options.cleanFrame()) base = cleanFrame(base);
        queue(minecraft, Session.cinematic(base, options));
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

    private Base cleanFrame(Base base) {
        CaptureRequest source = base.request;
        CaptureRequest clean = new CaptureRequest(
                source.presetId(), source.presetName(), source.mode(), source.resolution(),
                source.tileSize(), source.overlap(), source.pngCompression(), source.outputFormat(), source.jpegQuality(),
                true, true, true, source.includeMetadata());
        return new Base(clean, base.preflight);
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
        resetCinematicRuntime();
        long timerNanos = Math.multiplyExact((long) config.timerSeconds, 1_000_000_000L);
        long settleNanos = (newSession.mode == CameraMode.TIME || newSession.mode == CameraMode.CINEMATIC) ? 0L : settleNanos();
        preparation.start(nowNanos, timerNanos, settleNanos);
    }

    public void tick(Minecraft minecraft, long nowNanos) {
        Objects.requireNonNull(minecraft);
        if (terminalReadiness != null && nowNanos >= terminalUntilNanos) terminalReadiness = null;
        if (timeController.active() && (session == null || session.mode != CameraMode.CINEMATIC)) timeController.tick(minecraft, nowNanos);
        if (cinematicController.active()) cinematicController.tick(minecraft, nowNanos);
        if (session == null) return;

        if (session.mode == CameraMode.TIME && timeController.failed()) {
            failSession("Time control failed: " + timeController.errorMessage(), null);
            return;
        }
        if (session.mode == CameraMode.CINEMATIC && cinematicController.failed() && phase != Phase.CINEMATIC_RESTORE) {
            failSession("Cinematic scene control failed: " + cinematicController.errorMessage(), null);
            return;
        }
        if (activeCaptureId != null || startRequested) return;

        switch (phase) {
            case INITIAL_PREPARATION, FRAME_SETTLE -> tickPreparation(minecraft, nowNanos);
            case APPLY_TIME -> tickApplyTime(minecraft, nowNanos);
            case WAIT_INTERVAL -> {
                if (nowNanos >= nextFrameNotBeforeNanos) startCurrentFrame(minecraft);
            }
            case CINEMATIC -> tickCinematic(minecraft, nowNanos);
            case CINEMATIC_RESTORE -> tickCinematicRestore(nowNanos);
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
        if (session.mode == CameraMode.CINEMATIC && !postSceneSettle) {
            startCinematicPreparation(minecraft, nowNanos);
            return;
        }
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

    private void startCinematicPreparation(Minecraft minecraft, long nowNanos) {
        CinematicOptions options = session.cinematicOptions;
        if (options == null) {
            failSession("Cinematic session has no scene options", null);
            return;
        }
        cinematicFlow = new CinematicFlowMachine();
        cinematicFlow.start();
        try {
            if (options.cameraLock() || options.fovLock()) {
                cameraLockController.lock(minecraft, options.cameraLock(), options.fovLock());
            }
        } catch (RuntimeException error) {
            failSession("Unable to lock Cinematic camera", error);
            return;
        }

        cinematicSceneControllerUsed = requiresServerScene(options);
        if (cinematicSceneControllerUsed) {
            if (!cinematicController.begin(minecraft, options)) {
                failSession("Cinematic server scene controls are unavailable", null);
                return;
            }
        } else {
            cinematicFlow.snapshotReady();
            cinematicFlow.sceneApplied(options.waitForChunks());
            if (cinematicFlow.state() == CinematicFlowMachine.State.WAITING_FOR_CHUNKS) {
                cinematicChunkDeadlineNanos = saturatingAdd(nowNanos, Math.multiplyExact((long) options.chunkTimeoutMs(), 1_000_000L));
            }
        }
        phase = Phase.CINEMATIC;
    }

    private void tickCinematic(Minecraft minecraft, long nowNanos) {
        if (cinematicFlow == null || session == null || session.cinematicOptions == null) {
            failSession("Cinematic flow state is unavailable", null);
            return;
        }
        CinematicOptions options = session.cinematicOptions;
        switch (cinematicFlow.state()) {
            case SNAPSHOTTING -> {
                if (!cinematicSceneControllerUsed || cinematicController.snapshotReady()) cinematicFlow.snapshotReady();
            }
            case APPLYING_SCENE -> {
                if (cinematicSceneControllerUsed) {
                    if (!cinematicSceneApplyRequested) {
                        try {
                            cinematicController.applyScene();
                            cinematicSceneApplyRequested = true;
                        } catch (RuntimeException error) {
                            failSession("Unable to apply Cinematic scene", error);
                            return;
                        }
                    }
                    if (!cinematicController.clientObservedScene(minecraft)) return;
                }
                cinematicFlow.sceneApplied(options.waitForChunks());
                if (cinematicFlow.state() == CinematicFlowMachine.State.WAITING_FOR_CHUNKS) {
                    cinematicChunkDeadlineNanos = saturatingAdd(nowNanos, Math.multiplyExact((long) options.chunkTimeoutMs(), 1_000_000L));
                }
            }
            case WAITING_FOR_CHUNKS -> {
                cinematicChunkReadiness = chunkReadinessProbe.probe(minecraft, nowNanos, cinematicChunkDeadlineNanos);
                if (cinematicChunkReadiness.complete()) {
                    cinematicChunkTimedOut = false;
                    cinematicFlow.chunksReady();
                } else if (cinematicChunkReadiness.timedOut()) {
                    cinematicChunkTimedOut = true;
                }
            }
            case SETTLING -> tickCinematicSettle(minecraft, nowNanos, options);
            case READY_TO_CAPTURE -> {
                cinematicFlow.captureStarted();
                startCurrentFrame(minecraft);
            }
            case CAPTURING -> { }
            case RESTORING -> beginCinematicRestore(null, null, nowNanos);
            case COMPLETE -> finishCinematicSession(null, null, nowNanos);
            case IDLE -> failSession("Cinematic flow returned to idle unexpectedly", null);
        }
    }

    private void tickCinematicSettle(Minecraft minecraft, long nowNanos, CinematicOptions options) {
        if (cinematicSceneControllerUsed && options.worldFreeze()) {
            if (!cinematicFreezeRequested) {
                cinematicController.applyWorldFreeze();
                cinematicFreezeRequested = true;
            }
            if (!cinematicController.worldFreezeReady()) return;
        }

        if (!cinematicSettleStarted) {
            preparation.start(nowNanos, 0L, settleNanos());
            settleSignature = null;
            cinematicSettleStarted = true;
        }
        preparation.tick(nowNanos);
        ShotReadiness readiness = preparation.readiness(nowNanos);
        if (readiness.state() == ShotReadinessState.SETTLING_SHADERS && minecraft.player != null) {
            CameraSceneSignature current = signature(minecraft);
            if (settleSignature == null) settleSignature = current;
            else if (settleSignature.meaningfullyDiffers(current)) {
                preparation.noteSceneChanged(nowNanos);
                settleSignature = current;
                readiness = preparation.readiness(nowNanos);
            }
        }
        if (readiness.state() == ShotReadinessState.READY) cinematicFlow.settleReady();
    }

    private static boolean requiresServerScene(CinematicOptions options) {
        return options.worldFreeze()
                || options.timePreset() != CinematicTimePreset.CURRENT
                || options.weatherPreset() != CinematicWeatherPreset.CURRENT;
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
        if (session == null) return;
        if (session.mode == CameraMode.CINEMATIC) {
            if (cinematicFlow != null) cinematicFlow.cancel();
            beginCinematicRestore(ShotReadinessState.CANCELLED,
                    reason == null ? "Shot cancelled" : reason, nowNanos);
            return;
        }
        preparation.cancel();
        String groupId = session.groupId;
        restoreScene();
        clearSession();
        if (groupId != null) groupAnnotator.markIncomplete(groupId);
        setTerminal(ShotReadinessState.CANCELLED, reason == null ? "Shot cancelled" : reason, nowNanos);
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
            case CINEMATIC -> cinematicReadiness(nowNanos);
            case CINEMATIC_RESTORE -> new ShotReadiness(ShotReadinessState.FINALIZING, "Restoring Cinematic scene state", 0L);
            case CAPTURING -> new ShotReadiness(ShotReadinessState.CAPTURING, "Capturing " + session.frameStatus(), 0L);
            case IDLE -> new ShotReadiness(ShotReadinessState.READY, "Ready", 0L);
        };
    }

    private ShotReadiness cinematicReadiness(long nowNanos) {
        if (cinematicFlow == null) return new ShotReadiness(ShotReadinessState.PREPARING_SCENE, "Preparing Cinematic shot", 0L);
        return switch (cinematicFlow.state()) {
            case SNAPSHOTTING -> new ShotReadiness(ShotReadinessState.PREPARING_SCENE, "Snapshotting scene state", 0L);
            case APPLYING_SCENE -> new ShotReadiness(ShotReadinessState.PREPARING_SCENE, "Applying Cinematic scene", 0L);
            case WAITING_FOR_CHUNKS -> {
                if (cinematicChunkTimedOut) {
                    yield new ShotReadiness(ShotReadinessState.BLOCKED,
                            "Nearby chunks stopped loading — open F7 Controls to capture anyway or cancel", 0L);
                }
                String detail = cinematicChunkReadiness == null
                        ? "Checking nearby chunks"
                        : "Loading nearby chunks " + cinematicChunkReadiness.loadedChunks() + "/" + cinematicChunkReadiness.totalChunks()
                        + " (" + cinematicChunkReadiness.percent() + "%)";
                yield new ShotReadiness(ShotReadinessState.WAITING_FOR_CHUNKS, detail,
                        Math.max(0L, cinematicChunkDeadlineNanos - nowNanos));
            }
            case SETTLING -> {
                if (session.cinematicOptions != null && session.cinematicOptions.worldFreeze()
                        && cinematicSceneControllerUsed && !cinematicController.worldFreezeReady()) {
                    yield new ShotReadiness(ShotReadinessState.PREPARING_SCENE, "Freezing world for Cinematic frame", 0L);
                }
                ShotReadiness settle = preparation.readiness(nowNanos);
                yield settle.state() == ShotReadinessState.READY
                        ? new ShotReadiness(ShotReadinessState.PREPARING_SCENE, "Preparing shader settle", 0L)
                        : settle;
            }
            case READY_TO_CAPTURE -> new ShotReadiness(ShotReadinessState.READY, "Cinematic scene ready", 0L);
            case CAPTURING -> new ShotReadiness(ShotReadinessState.CAPTURING, "Capturing Cinematic frame", 0L);
            case RESTORING -> new ShotReadiness(ShotReadinessState.FINALIZING, "Restoring Cinematic scene state", 0L);
            case COMPLETE -> new ShotReadiness(ShotReadinessState.READY, "Cinematic shot complete", 0L);
            case IDLE -> new ShotReadiness(ShotReadinessState.PREPARING_SCENE, "Preparing Cinematic shot", 0L);
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

    public CinematicCapabilities cinematicCapabilities(Minecraft minecraft) {
        return cinematicController.capabilities(minecraft);
    }

    public boolean cinematicChunkTimedOut() {
        return session != null && session.mode == CameraMode.CINEMATIC && cinematicChunkTimedOut;
    }

    public ChunkReadiness cinematicChunkReadiness() {
        return cinematicChunkReadiness;
    }

    public void forceCinematicAfterChunkTimeout() {
        if (session == null || session.mode != CameraMode.CINEMATIC || cinematicFlow == null
                || cinematicFlow.state() != CinematicFlowMachine.State.WAITING_FOR_CHUNKS || !cinematicChunkTimedOut) {
            return;
        }
        cinematicChunkTimedOut = false;
        cinematicFlow.chunksReady();
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
        if (session.mode == CameraMode.CINEMATIC) {
            if (cinematicFlow != null && cinematicFlow.state() == CinematicFlowMachine.State.CAPTURING) cinematicFlow.finishCapture();
            beginCinematicRestore(null, null, System.nanoTime());
            return;
        }
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
        if (session != null && session.mode == CameraMode.CINEMATIC) {
            activeCaptureId = null;
            activeProgress = null;
            startRequested = false;
            if (cinematicFlow != null) cinematicFlow.cancel();
            beginCinematicRestore(ShotReadinessState.CANCELLED,
                    reason == null ? "Shot cancelled" : reason, System.nanoTime());
            return;
        }
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
        if (session != null && session.mode == CameraMode.CINEMATIC) {
            activeCaptureId = null;
            activeProgress = null;
            startRequested = false;
            if (cinematicFlow != null) cinematicFlow.cancel();
            String message = error == null ? detail : detail + ": " + safeMessage(error);
            beginCinematicRestore(ShotReadinessState.FAILED, message, System.nanoTime());
            return;
        }
        String groupId = session == null ? null : session.groupId;
        restoreScene();
        clearSession();
        if (groupId != null) groupAnnotator.markIncomplete(groupId);
        setTerminal(ShotReadinessState.FAILED, error == null ? detail : detail + ": " + safeMessage(error), System.nanoTime());
    }

    private void beginCinematicRestore(ShotReadinessState terminalState, String terminalReason, long nowNanos) {
        cinematicTerminalState = terminalState;
        cinematicTerminalReason = terminalReason;
        cameraLockController.unlock();
        if (cinematicFlow != null && cinematicFlow.state() != CinematicFlowMachine.State.RESTORING
                && cinematicFlow.state() != CinematicFlowMachine.State.COMPLETE) {
            cinematicFlow.cancel();
        }
        if (cinematicSceneControllerUsed && cinematicController.active()) cinematicController.restore();
        phase = Phase.CINEMATIC_RESTORE;
        if (!cinematicSceneControllerUsed) finishCinematicSession(terminalState, terminalReason, nowNanos);
    }

    private void tickCinematicRestore(long nowNanos) {
        if (cinematicSceneControllerUsed && !cinematicController.restorationComplete()) return;
        ShotReadinessState terminalState = cinematicTerminalState;
        String terminalReason = cinematicTerminalReason;
        if (cinematicController.failed() && terminalState == null) {
            terminalState = ShotReadinessState.FAILED;
            terminalReason = "Cinematic restoration reported an error: " + cinematicController.errorMessage();
        }
        finishCinematicSession(terminalState, terminalReason, nowNanos);
    }

    private void finishCinematicSession(ShotReadinessState terminalState, String terminalReason, long nowNanos) {
        if (cinematicFlow != null && cinematicFlow.state() == CinematicFlowMachine.State.RESTORING) cinematicFlow.restored();
        clearSession();
        if (terminalState != null) setTerminal(terminalState, terminalReason == null ? terminalState.name() : terminalReason, nowNanos);
        else terminalReadiness = null;
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
        resetCinematicRuntime();
    }

    private void resetCinematicRuntime() {
        cinematicFlow = null;
        cinematicSceneControllerUsed = false;
        cinematicSceneApplyRequested = false;
        cinematicFreezeRequested = false;
        cinematicSettleStarted = false;
        cinematicChunkReadiness = null;
        cinematicChunkTimedOut = false;
        cinematicChunkDeadlineNanos = 0L;
        cinematicTerminalState = null;
        cinematicTerminalReason = null;
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

    private enum Phase {
        IDLE,
        INITIAL_PREPARATION,
        APPLY_TIME,
        FRAME_SETTLE,
        WAIT_INTERVAL,
        CINEMATIC,
        CINEMATIC_RESTORE,
        CAPTURING
    }

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
        final CinematicOptions cinematicOptions;
        int index;

        private Session(CameraMode mode, Base base, String groupId, String groupType, List<Frame> frames,
                        long intervalNanos, SequencePreflight sequencePreflight, CinematicOptions cinematicOptions) {
            this.mode = mode;
            this.base = base;
            this.groupId = groupId;
            this.groupType = groupType;
            this.frames = List.copyOf(frames);
            this.intervalNanos = intervalNanos;
            this.sequencePreflight = sequencePreflight;
            this.cinematicOptions = cinematicOptions;
        }

        static Session photo(Base base) {
            if (base == null) return null;
            return new Session(CameraMode.PHOTO, base, null, null, List.of(new Frame("Photo", null)), 0L, null, null);
        }

        static Session sequence(CameraMode mode, Base base, String groupType, List<Frame> frames,
                                long intervalNanos, SequencePreflight preflight) {
            if (base == null) return null;
            return new Session(mode, base, UUID.randomUUID().toString(), groupType, frames, intervalNanos, preflight, null);
        }

        static Session cinematic(Base base, CinematicOptions options) {
            if (base == null) return null;
            return new Session(CameraMode.CINEMATIC, base, null, null, List.of(new Frame("Cinematic", null)), 0L, null,
                    Objects.requireNonNull(options));
        }

        Frame currentFrame() { return frames.get(index); }
        String frameStatus() { return frames.size() == 1 ? currentFrame().label : currentFrame().label + " (" + (index + 1) + "/" + frames.size() + ")"; }
    }
}
