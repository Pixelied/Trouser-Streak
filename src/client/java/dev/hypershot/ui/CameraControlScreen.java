package dev.hypershot.ui;

import dev.hypershot.HyperShotClient;
import dev.hypershot.config.CapturePreset;
import dev.hypershot.core.OutputFormat;
import dev.hypershot.core.camera.CameraMode;
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
        int panelWidth = Math.min(316, Math.max(240, width / 3));
        int x = width - panelWidth + 14;
        int controlWidth = panelWidth - 28;
        int y = 46;

        List<dev.hypershot.core.UiLayout.Rect> modes = dev.hypershot.core.UiLayout.distribute(
                new dev.hypershot.core.UiLayout.Rect(x, y, controlWidth, 20), 4, 4);
        addModeButton(modes.get(0), CameraMode.PHOTO, true);
        addModeButton(modes.get(1), CameraMode.BURST, true);
        addModeButton(modes.get(2), CameraMode.TIME, HyperShotClient.shotCoordinator().timeModeAvailable(minecraft));
        addModeButton(modes.get(3), CameraMode.CINEMATIC, false);
        y += 30;

        CapturePreset preset = HyperShotClient.config().activePreset();
        addRenderableWidget(Button.builder(Component.literal("Resolution: " + shortPresetName(preset)), b -> cycleResolution())
                .bounds(x, y, controlWidth, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Format: " + (preset.outputFormat == OutputFormat.PNG ? "PNG — Lossless" : "JPEG — Lossy")), b -> toggleFormat())
                .bounds(x, y, controlWidth, 20).build());
        y += 30;

        addRenderableWidget(Button.builder(Component.literal(timerLabel()), b -> cycleTimer())
                .bounds(x, y, controlWidth, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Shader settle: " + HyperShotTheme.titleCaseEnum(HyperShotClient.config().shaderSettleProfile)), b -> cycleSettle())
                .bounds(x, y, controlWidth, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Guide: " + HyperShotTheme.titleCaseEnum(HyperShotClient.config().guideType)), b -> cycleGuide())
                .bounds(x, y, controlWidth, 20).build());
        y += 30;

        if (HyperShotClient.config().cameraMode == CameraMode.BURST) {
            List<dev.hypershot.core.UiLayout.Rect> row = dev.hypershot.core.UiLayout.distribute(
                    new dev.hypershot.core.UiLayout.Rect(x, y, controlWidth, 20), 2, 4);
            addRenderableWidget(Button.builder(Component.literal("Shots: " + HyperShotClient.config().burstFrameCount), b -> cycleBurstCount())
                    .bounds(row.get(0).left(), row.get(0).top(), row.get(0).width(), row.get(0).height()).build());
            addRenderableWidget(Button.builder(Component.literal(burstIntervalLabel()), b -> cycleBurstInterval())
                    .bounds(row.get(1).left(), row.get(1).top(), row.get(1).width(), row.get(1).height()).build());
            y += 30;
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
            y += 30;
        }

        String shutter = switch (HyperShotClient.config().cameraMode) {
            case PHOTO -> "Take Photo";
            case BURST -> "Start Burst";
            case TIME -> "Capture Time Bracket";
            case CINEMATIC -> "Cinematic unavailable";
        };
        Button capture = addRenderableWidget(Button.builder(Component.literal(shutter), b -> {
                    HyperShotClient.queueCameraShot();
                    onClose();
                }).bounds(x, y, controlWidth, 20).build());
        capture.active = HyperShotClient.config().cameraMode != CameraMode.CINEMATIC
                && (HyperShotClient.config().cameraMode != CameraMode.TIME || HyperShotClient.shotCoordinator().timeModeAvailable(minecraft));
        y += 26;
        addRenderableWidget(Button.builder(Component.literal("Camera Behavior…"), b -> this.minecraft.gui.setScreen(new CameraSettingsScreen(this)))
                .bounds(x, y, controlWidth, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Capture Settings…"), b -> this.minecraft.gui.setScreen(new SettingsScreen(this)))
                .bounds(x, y, controlWidth, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Close controls"), b -> onClose())
                .bounds(x, y, controlWidth, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Exit camera mode"), b -> {
                    HyperShotClient.closeCameraViewfinder();
                    this.minecraft.gui.setScreen(null);
                }).bounds(x, y, controlWidth, 20).build());
    }

    private void addModeButton(dev.hypershot.core.UiLayout.Rect rect, CameraMode mode, boolean available) {
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
        int panelWidth = Math.min(316, Math.max(240, width / 3));
        int left = width - panelWidth;
        graphics.fill(left + 3, 3, width, height, 0x55000000);
        graphics.fill(left, 0, width, height, 0xE816191F);
        graphics.fill(left, 0, left + 3, height, HyperShotTheme.ACCENT);
        graphics.text(this.font, "HYPERSHOT CAMERA", left + 14, 13, HyperShotTheme.TEXT, true);
        graphics.text(this.font, modeLabel(HyperShotClient.config().cameraMode), left + 14, 27, HyperShotTheme.TEXT_MUTED, false);
        int helpY = Math.max(330, height - 78);
        String help = switch (HyperShotClient.config().cameraMode) {
            case PHOTO -> "One deliberate shot. F2 tap remains a single Photo even if another camera mode was selected here.";
            case BURST -> "Frames are captured sequentially and grouped together. At extreme resolutions this becomes a capture sequence, not a high-speed burst.";
            case TIME -> HyperShotClient.shotCoordinator().timeModeAvailable(minecraft)
                    ? "Singleplayer only. HyperShot temporarily pins each lighting time and restores the exact original time when the sequence ends or is cancelled."
                    : "Singleplayer only — a multiplayer server owns world time, so HyperShot will not fake this control.";
            case CINEMATIC -> "Cinematic scene controls are visible by design but stay locked until the safe scene-restoration layer is implemented.";
        };
        graphics.textWithWordWrap(this.font, Component.literal(help), left + 14, Math.min(helpY, height - 54),
                Math.max(1, panelWidth - 28), HyperShotTheme.TEXT_MUTED, false);
    }

    private void cycleResolution() {
        int current = PHOTO_PRESETS.indexOf(HyperShotClient.config().activePresetId);
        int next = Math.floorMod(current + 1, PHOTO_PRESETS.size());
        HyperShotClient.config().activePresetId = PHOTO_PRESETS.get(next);
        HyperShotClient.saveConfig();
        rebuildWidgets();
    }

    private void toggleFormat() {
        CapturePreset preset = HyperShotClient.config().activePreset();
        preset.outputFormat = preset.outputFormat == OutputFormat.PNG ? OutputFormat.JPEG : OutputFormat.PNG;
        HyperShotClient.saveConfig();
        rebuildWidgets();
    }

    private void cycleTimer() {
        int timer = HyperShotClient.config().timerSeconds;
        HyperShotClient.config().timerSeconds = switch (timer) { case 0 -> 3; case 3 -> 5; case 5 -> 10; default -> 0; };
        HyperShotClient.saveConfig();
        rebuildWidgets();
    }

    private void cycleSettle() {
        ShaderSettleProfile current = HyperShotClient.config().shaderSettleProfile;
        HyperShotClient.config().shaderSettleProfile = switch (current) {
            case OFF -> ShaderSettleProfile.QUICK;
            case QUICK -> ShaderSettleProfile.STANDARD;
            case STANDARD -> ShaderSettleProfile.DEEP;
            case DEEP, CUSTOM -> ShaderSettleProfile.OFF;
        };
        HyperShotClient.saveConfig();
        rebuildWidgets();
    }

    private void cycleGuide() {
        GuideType[] guides = GuideType.values();
        HyperShotClient.config().guideType = guides[(HyperShotClient.config().guideType.ordinal() + 1) % guides.length];
        HyperShotClient.saveConfig();
        rebuildWidgets();
    }

    private void cycleBurstCount() {
        int count = HyperShotClient.config().burstFrameCount;
        HyperShotClient.config().burstFrameCount = count < 3 ? 3 : count < 5 ? 5 : count < 10 ? 10 : 3;
        HyperShotClient.saveConfig();
        rebuildWidgets();
    }

    private void cycleBurstInterval() {
        long value = HyperShotClient.config().burstIntervalMs;
        HyperShotClient.config().burstIntervalMs = value == 0 ? 100 : value <= 100 ? 250 : value <= 250 ? 500 : value <= 500 ? 1_000 : 0;
        HyperShotClient.saveConfig();
        rebuildWidgets();
    }

    private void toggleTimeSequence() {
        HyperShotClient.config().timeUseCuratedSequence = !HyperShotClient.config().timeUseCuratedSequence;
        HyperShotClient.saveConfig();
        rebuildWidgets();
    }

    private void cycleTimeStep() {
        long step = HyperShotClient.config().timeStepTicks;
        HyperShotClient.config().timeStepTicks = step < 250 ? 250 : step < 500 ? 500 : step < 1_000 ? 1_000 : 250;
        HyperShotClient.saveConfig();
        rebuildWidgets();
    }

    private void toggleTimeSettle() {
        HyperShotClient.config().timeSettleBetweenFrames = !HyperShotClient.config().timeSettleBetweenFrames;
        HyperShotClient.saveConfig();
        rebuildWidgets();
    }

    private static String timerLabel() {
        int seconds = HyperShotClient.config().timerSeconds;
        return seconds == 0 ? "Timer: Off" : "Timer: " + seconds + " seconds";
    }

    private static String burstIntervalLabel() {
        long ms = HyperShotClient.config().burstIntervalMs;
        return ms == 0 ? "Every available frame" : ms < 1_000 ? ms + " ms" : (ms / 1_000) + " s";
    }

    private static String timeSequenceLabel() {
        return HyperShotClient.config().timeUseCuratedSequence ? "Lighting: 7-state cinematic day" : "Lighting: Custom time range";
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
            case "32k" -> "32K Experimental";
            case "custom" -> "Custom";
            default -> preset.name;
        };
    }
}
