package dev.hypershot.ui;

import dev.hypershot.HyperShotClient;
import dev.hypershot.config.CapturePreset;
import dev.hypershot.core.OutputFormat;
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
        int panelWidth = Math.min(292, Math.max(220, width / 3));
        int x = width - panelWidth + 14;
        int controlWidth = panelWidth - 28;
        int y = 48;

        CapturePreset preset = HyperShotClient.config().activePreset();
        addRenderableWidget(Button.builder(Component.literal("Resolution: " + shortPresetName(preset)), b -> cycleResolution())
                .bounds(x, y, controlWidth, 20).build());
        y += 26;
        addRenderableWidget(Button.builder(Component.literal("Format: " + (preset.outputFormat == OutputFormat.PNG ? "PNG — Lossless" : "JPEG — Lossy")), b -> toggleFormat())
                .bounds(x, y, controlWidth, 20).build());
        y += 34;
        addRenderableWidget(Button.builder(Component.literal(timerLabel()), b -> cycleTimer())
                .bounds(x, y, controlWidth, 20).build());
        y += 26;
        addRenderableWidget(Button.builder(Component.literal("Shader settle: " + HyperShotTheme.titleCaseEnum(HyperShotClient.config().shaderSettleProfile)), b -> cycleSettle())
                .bounds(x, y, controlWidth, 20).build());
        y += 26;
        addRenderableWidget(Button.builder(Component.literal("Guide: " + HyperShotTheme.titleCaseEnum(HyperShotClient.config().guideType)), b -> cycleGuide())
                .bounds(x, y, controlWidth, 20).build());
        y += 34;
        addRenderableWidget(Button.builder(Component.literal("Take Photo"), b -> {
                    HyperShotClient.queueCameraPhoto();
                    onClose();
                }).bounds(x, y, controlWidth, 20).build());
        y += 26;
        addRenderableWidget(Button.builder(Component.literal("Full HyperShot Settings"), b ->
                        this.minecraft.gui.setScreen(new SettingsScreen(this)))
                .bounds(x, y, controlWidth, 20).build());
        y += 26;
        addRenderableWidget(Button.builder(Component.literal("Close controls"), b -> onClose())
                .bounds(x, y, controlWidth, 20).build());
        y += 26;
        addRenderableWidget(Button.builder(Component.literal("Exit camera mode"), b -> {
                    HyperShotClient.closeCameraViewfinder();
                    this.minecraft.gui.setScreen(null);
                }).bounds(x, y, controlWidth, 20).build());
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(null);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int panelWidth = Math.min(292, Math.max(220, width / 3));
        int left = width - panelWidth;
        graphics.fill(left + 3, 3, width, height, 0x55000000);
        graphics.fill(left, 0, width, height, 0xE816191F);
        graphics.fill(left, 0, left + 3, height, HyperShotTheme.ACCENT);
        graphics.text(this.font, "HYPERSHOT CAMERA", left + 14, 14, HyperShotTheme.TEXT, true);
        graphics.text(this.font, "PHOTO", left + 14, 29, HyperShotTheme.TEXT_MUTED, false);
        int helpY = Math.min(height - 52, 292);
        graphics.textWithWordWrap(this.font,
                Component.literal("F2 takes the shot. Close this drawer to keep composing with normal mouselook. Shader Settle is a best-effort wait, not a guarantee for every shader pack."),
                left + 14, helpY, Math.max(1, panelWidth - 28), HyperShotTheme.TEXT_MUTED, false);
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
        HyperShotClient.config().timerSeconds = switch (timer) {
            case 0 -> 3;
            case 3 -> 5;
            case 5 -> 10;
            default -> 0;
        };
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
        int next = (HyperShotClient.config().guideType.ordinal() + 1) % guides.length;
        HyperShotClient.config().guideType = guides[next];
        HyperShotClient.saveConfig();
        rebuildWidgets();
    }

    private static String timerLabel() {
        int seconds = HyperShotClient.config().timerSeconds;
        return seconds == 0 ? "Timer: Off" : "Timer: " + seconds + " seconds";
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
