package dev.hypershot.ui;

import dev.hypershot.HyperShotClient;
import dev.hypershot.capture.CaptureRequest;
import dev.hypershot.config.CapturePreset;
import dev.hypershot.core.CaptureEstimate;
import dev.hypershot.core.CaptureEstimator;
import dev.hypershot.core.CaptureSpec;
import dev.hypershot.core.OutputFormat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

public final class QuickCaptureScreen extends HyperShotScreen {
    private int presetIndex;
    private CaptureEstimate estimate;
    private CaptureRequest request;

    public QuickCaptureScreen(Screen parent) {
        super(Component.translatable("screen.hypershot.quick.title"), parent);
        List<CapturePreset> presets = HyperShotClient.config().presets;
        for (int i = 0; i < presets.size(); i++) if (presets.get(i).id.equals(HyperShotClient.config().activePresetId)) presetIndex = i;
    }

    @Override
    protected void init() {
        super.init();
        recalculate();
        int center = this.width / 2;
        this.addRenderableWidget(Button.builder(Component.literal("‹"), button -> changePreset(-1)).bounds(center - 150, 54, 30, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(active().name), button -> {}).bounds(center - 112, 54, 224, 20).build()).active = false;
        this.addRenderableWidget(Button.builder(Component.literal("›"), button -> changePreset(1)).bounds(center + 120, 54, 30, 20).build());
        if (HyperShotClient.captureManager().isActive()) {
            String pauseLabel = HyperShotClient.captureManager().isPaused() ? "Resume" : "Pause";
            this.addRenderableWidget(Button.builder(Component.literal(pauseLabel), button -> {
                if (HyperShotClient.captureManager().isPaused()) HyperShotClient.captureManager().resume();
                else HyperShotClient.captureManager().pause();
                rebuildWidgets();
            }).bounds(center - 150, this.height - 62, 90, 22).build());
            this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
                HyperShotClient.captureManager().cancel("Cancelled from quick capture panel");
                onClose();
            }).bounds(center - 54, this.height - 62, 90, 22).build());
        } else {
            this.addRenderableWidget(Button.builder(Component.literal("Capture"), button -> capture())
                    .bounds(center - 150, this.height - 62, 186, 22).build());
        }
        this.addRenderableWidget(Button.builder(Component.literal("Gallery"), button -> HyperShotClient.openGallery(this)).bounds(center + 42, this.height - 62, 70, 22).build());
        this.addRenderableWidget(Button.builder(Component.literal("Settings"), button -> this.minecraft.gui.setScreen(new SettingsScreen(this))).bounds(center + 118, this.height - 62, 70, 22).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose()).bounds(center - 50, this.height - 32, 100, 20).build());
    }

    private void changePreset(int direction) {
        int size = HyperShotClient.config().presets.size();
        presetIndex = Math.floorMod(presetIndex + direction, size);
        HyperShotClient.config().activePresetId = active().id;
        HyperShotClient.saveConfig();
        recalculate();
        rebuildWidgets();
    }

    private void recalculate() {
        CapturePreset preset = active();
        int width = preset.width == 0 ? minecraft.gameRenderer.mainRenderTarget().width : preset.width;
        int height = preset.height == 0 ? minecraft.gameRenderer.mainRenderTarget().height : preset.height;
        request = CaptureRequest.from(preset, width, height);
        estimate = CaptureEstimator.estimate(new CaptureSpec(width, height, preset.tileSize, preset.overlap, 1, preset.outputFormat));
    }

    private void capture() {
        HyperShotClient.captureActivePreset();
        this.minecraft.gui.setScreen(parent);
    }

    private CapturePreset active() { return HyperShotClient.config().presets.get(presetIndex); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        drawHeader(graphics);
        int x = this.width / 2 - 150;
        int y = 92;
        graphics.text(this.font, "Output", x, y, 0xFF8EA6C4, false);
        graphics.text(this.font, request.resolution().width() + " × " + request.resolution().height() + " " + request.outputFormat().name(), x, y + 16, 0xFFFFFFFF, true);
        graphics.text(this.font, "Capture mode", x, y + 42, 0xFF8EA6C4, false);
        graphics.text(this.font, request.mode().name().replace('_', ' '), x, y + 58, 0xFFFFFFFF, true);
        graphics.text(this.font, "Estimated final file", x, y + 84, 0xFF8EA6C4, false);
        graphics.text(this.font, humanBytes(estimate.estimatedFinalBytes()), x, y + 100, 0xFFFFFFFF, true);
        graphics.text(this.font, "Temporary storage", x, y + 126, 0xFF8EA6C4, false);
        graphics.text(this.font, humanBytes(estimate.temporaryBytes()), x, y + 142, 0xFFFFFFFF, true);
        graphics.text(this.font, "Peak Java-side working memory", x, y + 168, 0xFF8EA6C4, false);
        graphics.text(this.font, humanBytes(estimate.peakHeapBytes()), x, y + 184, 0xFFFFFFFF, true);
        graphics.text(this.font, request.hideHud() ? "HUD hidden only in capture" : "HUD included", x, y + 214,
                request.hideHud() ? 0xFF70D893 : 0xFFFFC857, false);
    }

    private static String humanBytes(long bytes) {
        double value = bytes;
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++; }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }
}
