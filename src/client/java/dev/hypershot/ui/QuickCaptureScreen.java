package dev.hypershot.ui;

import dev.hypershot.HyperShotClient;
import dev.hypershot.capture.CaptureRequest;
import dev.hypershot.config.CapturePreset;
import dev.hypershot.core.CaptureEstimate;
import dev.hypershot.core.CaptureEstimator;
import dev.hypershot.core.CaptureSpec;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

public final class QuickCaptureScreen extends HyperShotScreen {
    private static final int GAP = 4;
    private int presetIndex;
    private CaptureEstimate estimate;
    private CaptureRequest request;

    public QuickCaptureScreen(Screen parent) {
        super(Component.translatable("screen.hypershot.quick.title"), parent);
        List<CapturePreset> presets = HyperShotClient.config().presets;
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).id.equals(HyperShotClient.config().activePresetId)) presetIndex = i;
        }
    }

    @Override
    protected void init() {
        super.init();
        recalculate();
        int contentWidth = Math.min(360, Math.max(1, this.width - 24));
        int left = (this.width - contentWidth) / 2;
        int arrowWidth = 28;
        int presetWidth = Math.max(60, contentWidth - arrowWidth * 2 - GAP * 2);

        this.addRenderableWidget(Button.builder(Component.literal("‹"), button -> changePreset(-1))
                .bounds(left, 44, arrowWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(active().name), button -> {})
                .bounds(left + arrowWidth + GAP, 44, presetWidth, 20).build()).active = false;
        this.addRenderableWidget(Button.builder(Component.literal("›"), button -> changePreset(1))
                .bounds(left + arrowWidth + GAP + presetWidth + GAP, 44, arrowWidth, 20).build());

        boolean activeCapture = HyperShotClient.captureManager().isActive();
        int actionCount = activeCapture ? 4 : 3;
        int actionWidth = Math.max(36, (contentWidth - GAP * (actionCount - 1)) / actionCount);
        int actionY = this.height - 58;
        int actionX = left;

        if (activeCapture) {
            String pauseLabel = HyperShotClient.captureManager().isPaused() ? "Resume" : "Pause";
            this.addRenderableWidget(Button.builder(Component.literal(pauseLabel), button -> {
                if (HyperShotClient.captureManager().isPaused()) HyperShotClient.captureManager().resume();
                else HyperShotClient.captureManager().pause();
                rebuildWidgets();
            }).bounds(actionX, actionY, actionWidth, 22).build());
            actionX += actionWidth + GAP;
            this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
                HyperShotClient.captureManager().cancel("Cancelled from quick capture panel");
                onClose();
            }).bounds(actionX, actionY, actionWidth, 22).build());
            actionX += actionWidth + GAP;
        } else {
            this.addRenderableWidget(Button.builder(Component.literal("Capture"), button -> capture())
                    .bounds(actionX, actionY, actionWidth, 22).build());
            actionX += actionWidth + GAP;
        }

        this.addRenderableWidget(Button.builder(Component.literal("Gallery"), button -> HyperShotClient.openGallery(this))
                .bounds(actionX, actionY, actionWidth, 22).build());
        actionX += actionWidth + GAP;
        this.addRenderableWidget(Button.builder(Component.literal(actionWidth < 68 ? "Config" : "Settings"),
                        button -> this.minecraft.gui.setScreen(new SettingsScreen(this)))
                .bounds(actionX, actionY, actionWidth, 22).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
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
        int contentWidth = Math.min(360, Math.max(1, this.width - 24));
        int left = (this.width - contentWidth) / 2;
        int columnGap = 12;
        int columnWidth = Math.max(70, (contentWidth - columnGap) / 2);
        int right = left + columnWidth + columnGap;
        int y = 78;

        drawMetric(graphics, left, y, columnWidth, "Output",
                request.resolution().width() + " × " + request.resolution().height() + " " + request.outputFormat().name());
        drawMetric(graphics, right, y, columnWidth, "Capture mode", request.mode().name().replace('_', ' '));
        y += 34;
        drawMetric(graphics, left, y, columnWidth, "Estimated final", humanBytes(estimate.estimatedFinalBytes()));
        drawMetric(graphics, right, y, columnWidth, "Temporary storage", humanBytes(estimate.temporaryBytes()));
        y += 34;
        drawMetric(graphics, left, y, columnWidth, "Peak Java memory", humanBytes(estimate.peakHeapBytes()));
        drawMetric(graphics, right, y, columnWidth, "Render workload", humanPixels(estimate.renderedPixels()));

        int statusY = Math.min(this.height - 72, y + 37);
        String status = request.hideHud() ? "HUD hidden only in capture" : "HUD included in capture";
        graphics.centeredText(this.font, status, this.width / 2, statusY,
                request.hideHud() ? 0xFF70D893 : 0xFFFFC857);
    }

    private void drawMetric(GuiGraphicsExtractor graphics, int x, int y, int width, String label, String value) {
        graphics.text(this.font, label, x, y, 0xFF8EA6C4, false);
        graphics.text(this.font, truncate(value, Math.max(8, width / 6)), x, y + 13, 0xFFFFFFFF, true);
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) return value;
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private static String humanPixels(long pixels) {
        if (pixels < 1_000) return pixels + " px";
        if (pixels < 1_000_000) return String.format(Locale.ROOT, "%.1f Kpx", pixels / 1_000.0);
        if (pixels < 1_000_000_000) return String.format(Locale.ROOT, "%.1f Mpx", pixels / 1_000_000.0);
        return String.format(Locale.ROOT, "%.2f Gpx", pixels / 1_000_000_000.0);
    }

    private static String humanBytes(long bytes) {
        double value = bytes;
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++; }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }
}
