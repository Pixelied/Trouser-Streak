package dev.hypershot.ui;

import dev.hypershot.HyperShotClient;
import dev.hypershot.config.CapturePreset;
import dev.hypershot.core.OutputFormat;
import dev.hypershot.core.UiLayout;
import dev.hypershot.core.camera.CameraMode;
import dev.hypershot.core.camera.CinematicCapabilities;
import dev.hypershot.core.camera.CinematicTimePreset;
import dev.hypershot.core.camera.CinematicWeatherPreset;
import dev.hypershot.core.camera.GuideType;
import dev.hypershot.core.camera.ShaderSettleProfile;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Cursor-enabled drawer shown over the world while the HUD viewfinder remains active. */
public final class CameraControlScreen extends Screen {
    private static final List<String> PHOTO_PRESETS = List.of("vanilla-plus", "4k", "8k", "16k", "32k", "custom");

    public CameraControlScreen() {
        super(Component.literal("HyperShot Camera Controls"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        int panelWidth = Math.min(332, Math.max(248, width / 3));
        int x = width - panelWidth + 14;
        int controlWidth = panelWidth - 28;
        int y = 46;
        CinematicCapabilities cinematicCapabilities = HyperShotClient.shotCoordinator().cinematicCapabilities(minecraft);

        List<UiLayout.Rect> modes = UiLayout.distribute(new UiLayout.Rect(x, y, controlWidth, 20), 4, 4);
        addModeButton(modes.get(0), CameraMode.PHOTO, true);
        addModeButton(modes.get(1), CameraMode.BURST, true);
        addModeButton(modes.get(2), CameraMode.TIME, HyperShotClient.shotCoordinator().timeModeAvailable(minecraft));
        addModeButton(modes.get(3), CameraMode.CINEMATIC, true);
        y += 28;

        if (HyperShotClient.shotCoordinator().cinematicChunkTimedOut()) {
            List<UiLayout.Rect> timeout = UiLayout.distribute(new UiLayout.Rect(x, y, controlWidth, 20), 2, 4);
            addRenderableWidget(Button.builder(Component.literal("Capture anyway"), b -> {
                        HyperShotClient.shotCoordinator().forceCinematicAfterChunkTimeout();
                        onClose();
                    }).bounds(timeout.get(0).left(), timeout.get(0).top(), timeout.get(0).width(), timeout.get(0).height()).build());
            addRenderableWidget(Button.builder(Component.literal("Cancel shot"), b -> {
                        HyperShotClient.shotCoordinator().cancel("Cinematic chunk wait cancelled");
                        onClose();
                    }).bounds(timeout.get(1).left(), timeout.get(1).top(), timeout.get(1).width(), timeout.get(1).height()).build());
            y += 26;
        }

        CapturePreset preset = HyperShotClient.config().activePreset();
        List<UiLayout.Rect> row = UiLayout.distribute(new UiLayout.Rect(x, y, controlWidth, 20), 2, 4);
        addRenderableWidget(Button.builder(Component.literal("Res: " + shortPresetName(preset)), b -> cycleResolution())
                .bounds(row.get(0).left(), row.get(0).top(), row.get(0).width(), row.get(0).height()).build());
        addRenderableWidget(Button.builder(Component.literal(preset.outputFormat == OutputFormat.PNG ? "PNG — Lossless" : "JPEG — Lossy"), b -> toggleFormat())
                .bounds(row.get(1).left(), row.get(1).top(), row.get(1).width(), row.get(1).height()).build());
        y += 24;

        row = UiLayout.distribute(new UiLayout.Rect(x, y, controlWidth, 20), 2, 4);
        addRenderableWidget(Button.builder(Component.literal(timerLabel()), b -> cycleTimer())
                .bounds(row.get(0).left(), row.get(0).top(), row.get(0).width(), row.get(0).height()).build());
        addRenderableWidget(Button.builder(Component.literal("Settle: " + HyperShotTheme.titleCaseEnum(HyperShotClient.config().shaderSettleProfile)), b -> cycleSettle())
                .bounds(row.get(1).left(), row.get(1).top(), row.get(1).width(), row.get(1).height()).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Guide: " + HyperShotTheme.titleCaseEnum(HyperShotClient.config().guideType)), b -> cycleGuide())
                .bounds(x, y, controlWidth, 20).build());
        y += 28;

        if (HyperShotClient.config().cameraMode == CameraMode.BURST) {
            row = UiLayout.distribute(new UiLayout.Rect(x, y, controlWidth, 20), 2, 4);
            addRenderableWidget(Button.builder(Component.literal("Shots: " + HyperShotClient.config().burstFrameCount), b -> cycleBurstCount())
                    .bounds(row.get(0).left(), row.get(0).top(), row.get(0).width(), row.get(0).height()).build());
            addRenderableWidget(Button.builder(Component.literal(burstIntervalLabel()), b -> cycleBurstInterval())
                    .bounds(row.get(1).left(), row.get(1).top(), row.get(1).width(), row.get(1).height()).build());
            y += 28;
        } else if (HyperShotClient.config().cameraMode == CameraMode.TIME) {
            addRenderableWidget(Button.builder(Component.literal(timeSequenceLabel()), b -> toggleTimeSequence())
                    .bounds(x, y, controlWidth, 20).build());
            y += 24;
            if (!HyperShotClient.config().timeUseCuratedSequence) {
                addRenderableWidget(Button.builder(Component.literal("Custom: " + HyperShotClient.config().timeStartTick + " → "
                                + HyperShotClient.config().timeEndTick + " / " + HyperShotClient.config().timeStepTicks + " ticks"), b -> cycleTimeStep())
                        .bounds(x, y, controlWidth, 20).build());
                y += 24;
            }
            addRenderableWidget(Button.builder(Component.literal((HyperShotClient.config().timeSettleBetweenFrames ? "✓ " : "") + "Settle after each lighting change"), b -> toggleTimeSettle())
                    .bounds(x, y, controlWidth, 20).build());
            y += 28;
        } else if (HyperShotClient.config().cameraMode == CameraMode.CINEMATIC) {
            y = initCinematicControls(x, controlWidth, y, cinematicCapabilities);
        }

        String shutter = switch (HyperShotClient.config().cameraMode) {
            case PHOTO -> "Take Photo";
            case BURST -> "Start Burst";
            case TIME -> "Capture Time Bracket";
            case CINEMATIC -> "Take Cinematic Photo";
        };
        Button capture = addRenderableWidget(Button.builder(Component.literal(shutter), b -> {
                    HyperShotClient.queueCameraShot();
                    onClose();
                }).bounds(x, y, controlWidth, 20).build());
        boolean busy = HyperShotClient.captureManager().isActive() || HyperShotClient.shotCoordinator().hasQueuedShot();
        capture.active = !busy && (HyperShotClient.config().cameraMode != CameraMode.TIME
                || HyperShotClient.shotCoordinator().timeModeAvailable(minecraft));
        y += 26;

        row = UiLayout.distribute(new UiLayout.Rect(x, y, controlWidth, 20), 2, 4);
        addRenderableWidget(Button.builder(Component.literal("Camera Behavior…"), b -> this.minecraft.gui.setScreen(new CameraSettingsScreen(this)))
                .bounds(row.get(0).left(), row.get(0).top(), row.get(0).width(), row.get(0).height()).build());
        addRenderableWidget(Button.builder(Component.literal("Capture Settings…"), b -> this.minecraft.gui.setScreen(new SettingsScreen(this)))
                .bounds(row.get(1).left(), row.get(1).top(), row.get(1).width(), row.get(1).height()).build());
        y += 24;

        row = UiLayout.distribute(new UiLayout.Rect(x, y, controlWidth, 20), 2, 4);
        addRenderableWidget(Button.builder(Component.literal("Close controls"), b -> onClose())
                .bounds(row.get(0).left(), row.get(0).top(), row.get(0).width(), row.get(0).height()).build());
        addRenderableWidget(Button.builder(Component.literal("Exit camera"), b -> {
                    HyperShotClient.closeCameraViewfinder();
                    this.minecraft.gui.setScreen(null);
                }).bounds(row.get(1).left(), row.get(1).top(), row.get(1).width(), row.get(1).height()).build());
    }

    private int initCinematicControls(int x, int width, int y, CinematicCapabilities capabilities) {
        List<UiLayout.Rect> row = UiLayout.distribute(new UiLayout.Rect(x, y, width, 20), 2, 4);
        Button freeze = addRenderableWidget(Button.builder(Component.literal(capabilities.worldFreeze()
                        ? "Freeze: " + onOff(HyperShotClient.config().cinematicWorldFreeze)
                        : "Freeze: Server-owned"), b -> toggleCinematicFreeze())
                .bounds(row.get(0).left(), row.get(0).top(), row.get(0).width(), row.get(0).height()).build());
        freeze.active = capabilities.worldFreeze();
        Button time = addRenderableWidget(Button.builder(Component.literal(capabilities.authoritativeTime()
                        ? "Time: " + cinematicTimeLabel(HyperShotClient.config().cinematicTimePreset)
                        : "Time: Server-owned"), b -> cycleCinematicTime())
                .bounds(row.get(1).left(), row.get(1).top(), row.get(1).width(), row.get(1).height()).build());
        time.active = capabilities.authoritativeTime();
        y += 24;

        row = UiLayout.distribute(new UiLayout.Rect(x, y, width, 20), 2, 4);
        Button weather = addRenderableWidget(Button.builder(Component.literal(capabilities.authoritativeWeather()
                        ? "Weather: " + HyperShotTheme.titleCaseEnum(HyperShotClient.config().cinematicWeatherPreset)
                        : "Weather: Server-owned"), b -> cycleCinematicWeather())
                .bounds(row.get(0).left(), row.get(0).top(), row.get(0).width(), row.get(0).height()).build());
        weather.active = capabilities.authoritativeWeather();
        Button chunks = addRenderableWidget(Button.builder(Component.literal("Chunks: " + (HyperShotClient.config().cinematicWaitForChunks ? "Wait" : "Don't wait")),
                        b -> toggleCinematicChunkWait())
                .bounds(row.get(1).left(), row.get(1).top(), row.get(1).width(), row.get(1).height()).build());
        chunks.active = capabilities.chunkReadiness();
        y += 24;

        row = UiLayout.distribute(new UiLayout.Rect(x, y, width, 20), 2, 4);
        Button camera = addRenderableWidget(Button.builder(Component.literal("Camera: " + (HyperShotClient.config().cinematicCameraLock ? "Lock" : "Free")),
                        b -> toggleCinematicCameraLock())
                .bounds(row.get(0).left(), row.get(0).top(), row.get(0).width(), row.get(0).height()).build());
        camera.active = capabilities.cameraLock();
        Button fov = addRenderableWidget(Button.builder(Component.literal("FOV: " + (HyperShotClient.config().cinematicFovLock ? "Lock" : "Free")),
                        b -> toggleCinematicFovLock())
                .bounds(row.get(1).left(), row.get(1).top(), row.get(1).width(), row.get(1).height()).build());
        fov.active = capabilities.fovLock();
        y += 24;

        row = UiLayout.distribute(new UiLayout.Rect(x, y, width, 20), 2, 4);
        addRenderableWidget(Button.builder(Component.literal("Clean frame: " + onOff(HyperShotClient.config().cinematicCleanFrame)), b -> toggleCinematicCleanFrame())
                .bounds(row.get(0).left(), row.get(0).top(), row.get(0).width(), row.get(0).height()).build());
        addRenderableWidget(Button.builder(Component.literal("Timeout: " + (HyperShotClient.config().cinematicChunkTimeoutMs / 1_000) + "s"), b -> cycleCinematicChunkTimeout())
                .bounds(row.get(1).left(), row.get(1).top(), row.get(1).width(), row.get(1).height()).build());
        return y + 28;
    }

    private void addModeButton(UiLayout.Rect rect, CameraMode mode, boolean available) {
        boolean selected = HyperShotClient.config().cameraMode == mode;
        Button button = addRenderableWidget(Button.builder(Component.literal((selected ? "✓ " : "") + modeLabel(mode)), b -> {
                    HyperShotClient.config().cameraMode = mode;
                    HyperShotClient.saveConfig();
                    rebuildWidgets();
                }).bounds(rect.left(), rect.top(), rect.width(), rect.height()).build());
        button.active = available;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(null);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int panelWidth = Math.min(332, Math.max(248, width / 3));
        int left = width - panelWidth;
        graphics.fill(left + 3, 3, width, height, 0x55000000);
        graphics.fill(left, 0, width, height, 0xE816191F);
        graphics.fill(left, 0, left + 3, height, HyperShotTheme.ACCENT);
        graphics.text(this.font, "HYPERSHOT CAMERA", left + 14, 13, HyperShotTheme.TEXT, true);
        graphics.text(this.font, modeLabel(HyperShotClient.config().cameraMode), left + 14, 27, HyperShotTheme.TEXT_MUTED, false);

        if (height < 360) return;
        int helpY = height - 52;
        CinematicCapabilities capabilities = HyperShotClient.shotCoordinator().cinematicCapabilities(minecraft);
        String help = switch (HyperShotClient.config().cameraMode) {
            case PHOTO -> "One deliberate shot. F2 tap outside the viewfinder always remains a single Photo.";
            case BURST -> "Frames capture sequentially and stay grouped. Extreme-resolution Burst prioritizes image integrity over speed.";
            case TIME -> HyperShotClient.shotCoordinator().timeModeAvailable(minecraft)
                    ? "Singleplayer: HyperShot pins each lighting state, settles, captures, then restores the exact original WorldClock value."
                    : "Multiplayer servers own world time, so Time mode is disabled rather than faked.";
            case CINEMATIC -> capabilities.authoritativeTime()
                    ? "Cinematic snapshots scene state, waits for chunks, optionally freezes time/weather/world, settles shaders, captures, then restores everything."
                    : "Multiplayer Cinematic keeps server time/weather/freeze unchanged; client camera/FOV lock, chunk readiness and Clean Frame still work.";
        };
        graphics.textWithWordWrap(this.font, Component.literal(help), left + 14, helpY,
                Math.max(1, panelWidth - 28), HyperShotTheme.TEXT_MUTED, false);
    }

    private void cycleResolution() {
        int current = PHOTO_PRESETS.indexOf(HyperShotClient.config().activePresetId);
        int next = Math.floorMod(current + 1, PHOTO_PRESETS.size());
        HyperShotClient.config().activePresetId = PHOTO_PRESETS.get(next);
        saveAndRebuild();
    }

    private void toggleFormat() {
        CapturePreset preset = HyperShotClient.config().activePreset();
        preset.outputFormat = preset.outputFormat == OutputFormat.PNG ? OutputFormat.JPEG : OutputFormat.PNG;
        saveAndRebuild();
    }

    private void cycleTimer() {
        int timer = HyperShotClient.config().timerSeconds;
        HyperShotClient.config().timerSeconds = switch (timer) { case 0 -> 3; case 3 -> 5; case 5 -> 10; default -> 0; };
        saveAndRebuild();
    }

    private void cycleSettle() {
        ShaderSettleProfile current = HyperShotClient.config().shaderSettleProfile;
        HyperShotClient.config().shaderSettleProfile = switch (current) {
            case OFF -> ShaderSettleProfile.QUICK;
            case QUICK -> ShaderSettleProfile.STANDARD;
            case STANDARD -> ShaderSettleProfile.DEEP;
            case DEEP, CUSTOM -> ShaderSettleProfile.OFF;
        };
        saveAndRebuild();
    }

    private void cycleGuide() {
        GuideType[] guides = GuideType.values();
        HyperShotClient.config().guideType = guides[(HyperShotClient.config().guideType.ordinal() + 1) % guides.length];
        saveAndRebuild();
    }

    private void cycleBurstCount() {
        int count = HyperShotClient.config().burstFrameCount;
        HyperShotClient.config().burstFrameCount = count < 3 ? 3 : count < 5 ? 5 : count < 10 ? 10 : 3;
        saveAndRebuild();
    }

    private void cycleBurstInterval() {
        long value = HyperShotClient.config().burstIntervalMs;
        HyperShotClient.config().burstIntervalMs = value == 0 ? 100 : value <= 100 ? 250 : value <= 250 ? 500 : value <= 500 ? 1_000 : 0;
        saveAndRebuild();
    }

    private void toggleTimeSequence() {
        HyperShotClient.config().timeUseCuratedSequence = !HyperShotClient.config().timeUseCuratedSequence;
        saveAndRebuild();
    }

    private void cycleTimeStep() {
        long step = HyperShotClient.config().timeStepTicks;
        HyperShotClient.config().timeStepTicks = step < 250 ? 250 : step < 500 ? 500 : step < 1_000 ? 1_000 : 250;
        saveAndRebuild();
    }

    private void toggleTimeSettle() {
        HyperShotClient.config().timeSettleBetweenFrames = !HyperShotClient.config().timeSettleBetweenFrames;
        saveAndRebuild();
    }

    private void toggleCinematicFreeze() {
        HyperShotClient.config().cinematicWorldFreeze = !HyperShotClient.config().cinematicWorldFreeze;
        saveAndRebuild();
    }

    private void cycleCinematicTime() {
        CinematicTimePreset[] values = CinematicTimePreset.values();
        HyperShotClient.config().cinematicTimePreset = values[(HyperShotClient.config().cinematicTimePreset.ordinal() + 1) % values.length];
        saveAndRebuild();
    }

    private void cycleCinematicWeather() {
        CinematicWeatherPreset[] values = CinematicWeatherPreset.values();
        HyperShotClient.config().cinematicWeatherPreset = values[(HyperShotClient.config().cinematicWeatherPreset.ordinal() + 1) % values.length];
        saveAndRebuild();
    }

    private void toggleCinematicChunkWait() {
        HyperShotClient.config().cinematicWaitForChunks = !HyperShotClient.config().cinematicWaitForChunks;
        saveAndRebuild();
    }

    private void toggleCinematicCameraLock() {
        HyperShotClient.config().cinematicCameraLock = !HyperShotClient.config().cinematicCameraLock;
        saveAndRebuild();
    }

    private void toggleCinematicFovLock() {
        HyperShotClient.config().cinematicFovLock = !HyperShotClient.config().cinematicFovLock;
        saveAndRebuild();
    }

    private void toggleCinematicCleanFrame() {
        HyperShotClient.config().cinematicCleanFrame = !HyperShotClient.config().cinematicCleanFrame;
        saveAndRebuild();
    }

    private void cycleCinematicChunkTimeout() {
        int value = HyperShotClient.config().cinematicChunkTimeoutMs;
        HyperShotClient.config().cinematicChunkTimeoutMs = value < 5_000 ? 5_000 : value < 10_000 ? 10_000 : value < 20_000 ? 20_000 : value < 30_000 ? 30_000 : 5_000;
        saveAndRebuild();
    }

    private void saveAndRebuild() {
        HyperShotClient.saveConfig();
        rebuildWidgets();
    }

    private static String timerLabel() {
        int seconds = HyperShotClient.config().timerSeconds;
        return seconds == 0 ? "Timer: Off" : "Timer: " + seconds + "s";
    }

    private static String burstIntervalLabel() {
        long ms = HyperShotClient.config().burstIntervalMs;
        return ms == 0 ? "Every frame" : ms < 1_000 ? ms + " ms" : (ms / 1_000) + " s";
    }

    private static String timeSequenceLabel() {
        return HyperShotClient.config().timeUseCuratedSequence ? "Lighting: 7-state cinematic day" : "Lighting: Custom time range";
    }

    private static String cinematicTimeLabel(CinematicTimePreset preset) {
        return switch (preset) {
            case CURRENT -> "Current";
            case SUNRISE -> "Sunrise";
            case MORNING -> "Morning";
            case NOON -> "Noon";
            case GOLDEN_HOUR -> "Golden Hour";
            case SUNSET -> "Sunset";
            case BLUE_HOUR -> "Blue Hour";
            case NIGHT -> "Night";
        };
    }

    private static String onOff(boolean value) {
        return value ? "On" : "Off";
    }

    private static String modeLabel(CameraMode mode) {
        return switch (mode) { case PHOTO -> "Photo"; case BURST -> "Burst"; case TIME -> "Time"; case CINEMATIC -> "Cinematic"; };
    }

    private static String shortPresetName(CapturePreset preset) {
        return switch (preset.id) {
            case "vanilla-plus" -> "Native";
            case "4k" -> "4K";
            case "8k" -> "8K";
            case "16k" -> "16K";
            case "32k" -> "32K Exp.";
            case "custom" -> "Custom";
            default -> preset.name;
        };
    }
}
