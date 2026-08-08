package dev.hypershot.ui;

import dev.hypershot.HyperShotClient;
import dev.hypershot.capture.CaptureRequest;
import dev.hypershot.config.CapturePreset;
import dev.hypershot.core.CaptureEstimate;
import dev.hypershot.core.CaptureEstimator;
import dev.hypershot.core.CaptureSpec;
import dev.hypershot.core.UiLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class QuickCaptureScreen extends HyperShotScreen {
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
        UiLayout layout = layout(false);
        int bodyWidth = layout.content().width() - 28;
        int selectorY = layout.content().top() + 14;
        int selectorWidth = Math.min(520, bodyWidth);
        int selectorX = layout.content().centerX() - selectorWidth / 2;

        this.addRenderableWidget(Button.builder(Component.literal("‹"), button -> changePreset(-1))
                .bounds(selectorX, selectorY, 30, 22).build());
        Button preset = this.addRenderableWidget(Button.builder(Component.literal(active().name), button -> {})
                .bounds(selectorX + 38, selectorY, selectorWidth - 76, 22).build());
        preset.active = false;
        this.addRenderableWidget(Button.builder(Component.literal("›"), button -> changePreset(1))
                .bounds(selectorX + selectorWidth - 30, selectorY, 30, 22).build());

        boolean activeCapture = HyperShotClient.captureManager().isActive();
        int count = activeCapture ? 5 : 4;
        List<UiLayout.Rect> actions = footerButtons(layout, count);
        int action = 0;
        if (activeCapture) {
            String pauseLabel = HyperShotClient.captureManager().isPaused() ? "Resume" : "Pause";
            UiLayout.Rect pause = actions.get(action++);
            this.addRenderableWidget(Button.builder(Component.literal(pauseLabel), button -> {
                if (HyperShotClient.captureManager().isPaused()) HyperShotClient.captureManager().resume();
                else HyperShotClient.captureManager().pause();
                rebuildWidgets();
            }).bounds(pause.left(), pause.top(), pause.width(), pause.height()).build());
            UiLayout.Rect cancel = actions.get(action++);
            this.addRenderableWidget(Button.builder(Component.literal(layout.compact() ? "Cancel" : "Cancel capture"), button -> {
                HyperShotClient.captureManager().cancel("Cancelled from quick capture panel");
                onClose();
            }).bounds(cancel.left(), cancel.top(), cancel.width(), cancel.height()).build());
        } else {
            UiLayout.Rect captureRect = actions.get(action++);
            Button capture = this.addRenderableWidget(Button.builder(Component.literal(layout.compact() ? "Capture" : "Capture now"), button -> capture())
                    .bounds(captureRect.left(), captureRect.top(), captureRect.width(), captureRect.height()).build());
            capture.active = this.minecraft.level != null;
        }
        UiLayout.Rect gallery = actions.get(action++);
        this.addRenderableWidget(Button.builder(Component.literal("Gallery"), button -> HyperShotClient.openGallery(this))
                .bounds(gallery.left(), gallery.top(), gallery.width(), gallery.height()).build());
        UiLayout.Rect settings = actions.get(action++);
        this.addRenderableWidget(Button.builder(Component.literal(layout.compact() ? "Config" : "Settings"),
                        button -> this.minecraft.gui.setScreen(new SettingsScreen(this)))
                .bounds(settings.left(), settings.top(), settings.width(), settings.height()).build());
        UiLayout.Rect done = actions.get(action);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(done.left(), done.top(), done.width(), done.height()).build());
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

    private CapturePreset active() {
        return HyperShotClient.config().presets.get(presetIndex);
    }

    @Override
    protected void extractHyperShotBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        UiLayout layout = layout(false);
        drawChrome(graphics, layout, Component.literal("Fast access to real capture settings"));
        drawContentPanels(graphics, layout);

        int x = contentLeft(layout);
        int y = layout.content().top() + 50;
        int available = Math.max(1, layout.content().width() - 28);
        int remaining = Math.max(1, layout.content().bottom() - y - 12);

        if (layout.compact() || remaining < 150) {
            UiLayout.Rect summary = new UiLayout.Rect(x, y, available, remaining);
            HyperShotTheme.raisedPanel(graphics, summary);
            HyperShotTheme.accentBar(graphics, summary);
            int textX = summary.left() + 12;
            int textY = summary.top() + 10;
            graphics.text(this.font, "CAPTURE SUMMARY", textX, textY, HyperShotTheme.TEXT_DIM, false);
            if (summary.height() >= 32) {
                graphics.text(this.font, request.resolution().width() + " × " + request.resolution().height() + "  •  "
                                + request.outputFormat().name() + "  •  " + HyperShotTheme.titleCaseEnum(request.mode()),
                        textX, textY + 17, HyperShotTheme.TEXT, true);
            }
            if (summary.height() >= 50) {
                graphics.text(this.font, "Final " + HyperShotTheme.humanBytes(estimate.estimatedFinalBytes())
                                + "  •  Temp " + HyperShotTheme.humanBytes(estimate.temporaryBytes()),
                        textX, textY + 34, HyperShotTheme.TEXT_MUTED, false);
            }
            if (summary.height() >= 68) {
                graphics.text(this.font, request.hideHud() ? "HUD hidden only in capture" : "HUD included",
                        textX, textY + 51, request.hideHud() ? HyperShotTheme.SUCCESS : HyperShotTheme.WARNING, false);
            }
        } else {
            int gap = 10;
            int columnWidth = (available - gap) / 2;
            int cardHeight = Math.min(126, remaining);
            UiLayout.Rect outputCard = new UiLayout.Rect(x, y, columnWidth, cardHeight);
            UiLayout.Rect workloadCard = new UiLayout.Rect(outputCard.right() + gap, y, columnWidth, cardHeight);
            HyperShotTheme.raisedPanel(graphics, outputCard);
            HyperShotTheme.accentBar(graphics, outputCard);
            HyperShotTheme.raisedPanel(graphics, workloadCard);

            int textX = outputCard.left() + 12;
            graphics.text(this.font, "OUTPUT", textX, outputCard.top() + 10, HyperShotTheme.TEXT_DIM, false);
            graphics.text(this.font, request.resolution().width() + " × " + request.resolution().height(), textX, outputCard.top() + 28, HyperShotTheme.TEXT, true);
            graphics.text(this.font, request.outputFormat().name() + " • " + HyperShotTheme.titleCaseEnum(request.mode()),
                    textX, outputCard.top() + 46, HyperShotTheme.TEXT_MUTED, false);
            graphics.text(this.font, request.hideHud() ? "HUD hidden only in capture" : "HUD included",
                    textX, outputCard.top() + 70, request.hideHud() ? HyperShotTheme.SUCCESS : HyperShotTheme.WARNING, false);
            graphics.text(this.font, request.hideHand() ? "Hand hidden" : "Hand included",
                    textX, outputCard.top() + 86, request.hideHand() ? HyperShotTheme.SUCCESS : HyperShotTheme.TEXT_MUTED, false);

            int workX = workloadCard.left() + 12;
            graphics.text(this.font, "WORKLOAD ESTIMATE", workX, workloadCard.top() + 10, HyperShotTheme.TEXT_DIM, false);
            HyperShotTheme.labelValue(graphics, this.font, "Final file", HyperShotTheme.humanBytes(estimate.estimatedFinalBytes()), workX, workloadCard.top() + 28);
            HyperShotTheme.labelValue(graphics, this.font, "Temporary storage", HyperShotTheme.humanBytes(estimate.temporaryBytes()), workX, workloadCard.top() + 62);
            if (workloadCard.width() >= 250) {
                HyperShotTheme.labelValue(graphics, this.font, "Peak Java memory", HyperShotTheme.humanBytes(estimate.peakHeapBytes()),
                        workX + workloadCard.width() / 2, workloadCard.top() + 28);
            }
        }

        if (this.minecraft.level == null) {
            graphics.centeredText(this.font, "Enter a world to capture. Gallery and settings remain available.",
                    layout.content().centerX(), layout.content().bottom() - 22, HyperShotTheme.WARNING);
        } else if (HyperShotClient.captureManager().isActive()) {
            graphics.centeredText(this.font, HyperShotClient.captureManager().isPaused() ? "Capture paused" : "Capture in progress",
                    layout.content().centerX(), layout.content().bottom() - 22, HyperShotTheme.ACCENT_BRIGHT);
        }
    }
}
