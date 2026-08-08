package dev.hypershot.ui;

import dev.hypershot.HyperShotClient;
import dev.hypershot.core.UiLayout;
import dev.hypershot.core.camera.F2Behavior;
import dev.hypershot.core.camera.GuideType;
import dev.hypershot.core.camera.ShaderSettleProfile;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class CameraSettingsScreen extends HyperShotScreen {
    private Page page = Page.PHOTOGRAPHY;
    private String timerText;
    private String settleText;

    public CameraSettingsScreen(Screen parent) {
        super(Component.literal("HyperShot Camera Behavior"), parent);
    }

    @Override
    protected void init() {
        super.init();
        if (timerText == null) timerText = Integer.toString(HyperShotClient.config().timerSeconds);
        if (settleText == null) settleText = Integer.toString(HyperShotClient.config().customShaderSettleMs);

        UiLayout layout = layout(false);
        int x = contentLeft(layout);
        int available = Math.max(1, layout.content().width() - 28);
        int y = layout.content().top() + 16;
        List<UiLayout.Rect> tabs = UiLayout.distribute(new UiLayout.Rect(x, y, Math.min(420, available), 20), 2, 6);
        addButton(tabs.get(0), (page == Page.PHOTOGRAPHY ? "✓ " : "") + "Photography", () -> setPage(Page.PHOTOGRAPHY));
        addButton(tabs.get(1), (page == Page.INTERACTION ? "✓ " : "") + "F2 & Viewfinder", () -> setPage(Page.INTERACTION));
        y += 42;

        if (page == Page.PHOTOGRAPHY) initPhotography(x, available, y);
        else initInteraction(x, available, y);

        List<UiLayout.Rect> footer = footerButtons(layout, 3);
        Button viewfinder = addButton(footer.get(0), "Open Camera", () -> {
            HyperShotClient.openCameraViewfinder();
            this.minecraft.gui.setScreen(null);
        });
        viewfinder.active = minecraft.level != null;
        addButton(footer.get(1), "Capture Settings", () -> this.minecraft.gui.setScreen(new SettingsScreen(this)));
        addButton(footer.get(2), "Done", this::onClose);
    }

    private void initPhotography(int x, int width, int y) {
        int gap = 6;
        int half = Math.max(90, (width - gap) / 2);
        List<UiLayout.Rect> row = UiLayout.distribute(new UiLayout.Rect(x, y, width, 20), 2, gap);
        addButton(row.get(0), timerLabel(), this::cycleTimer);
        addButton(row.get(1), "Guide: " + HyperShotTheme.titleCaseEnum(HyperShotClient.config().guideType), this::cycleGuide);
        y += 28;
        row = UiLayout.distribute(new UiLayout.Rect(x, y, width, 20), 2, gap);
        addButton(row.get(0), "Guide opacity: " + Math.round(HyperShotClient.config().guideOpacity * 100) + "%", this::cycleGuideOpacity);
        addButton(row.get(1), "Shader settle: " + HyperShotTheme.titleCaseEnum(HyperShotClient.config().shaderSettleProfile), this::cycleSettle);
        y += 34;

        EditBox timer = new EditBox(this.font, x, y, half, 20, Component.literal("Custom timer seconds"));
        timer.setMaxLength(2);
        timer.setValue(timerText);
        timer.setResponder(value -> timerText = value);
        this.addRenderableWidget(timer);
        EditBox settle = new EditBox(this.font, x + half + gap, y, half, 20, Component.literal("Custom shader settle milliseconds"));
        settle.setMaxLength(5);
        settle.setValue(settleText);
        settle.setResponder(value -> settleText = value);
        this.addRenderableWidget(settle);
        y += 26;
        addButton(new UiLayout.Rect(x, y, width, 20), "Apply custom timer / shader settle", this::applyCustomValues);
    }

    private void initInteraction(int x, int width, int y) {
        int gap = 6;
        List<UiLayout.Rect> row = UiLayout.distribute(new UiLayout.Rect(x, y, width, 20), 2, gap);
        addButton(row.get(0), "F2: " + f2Label(HyperShotClient.config().f2Behavior), this::cycleF2Behavior);
        addButton(row.get(1), "Hold threshold: " + HyperShotClient.config().f2HoldThresholdMs + " ms", this::cycleHoldThreshold);
        y += 28;
        row = UiLayout.distribute(new UiLayout.Rect(x, y, width, 20), 2, gap);
        addButton(row.get(0), (HyperShotClient.config().cameraOverlayFade ? "✓ " : "") + "Fade viewfinder when idle", this::toggleOverlayFade);
        addButton(row.get(1), (HyperShotClient.config().countdownSounds ? "✓ " : "") + "Countdown sounds", this::toggleCountdownSounds);
        y += 34;
        addButton(new UiLayout.Rect(x, y, width, 20), "Restore recommended camera behavior", this::restoreRecommended);
    }

    @Override
    protected void extractHyperShotBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        UiLayout layout = layout(false);
        drawChrome(graphics, layout, Component.literal(page == Page.PHOTOGRAPHY ? "Photo preparation defaults" : "Fast screenshot interaction"));
        drawContentPanels(graphics, layout);
        int x = contentLeft(layout);
        int width = Math.max(1, layout.content().width() - 28);
        int y = layout.content().top() + 108;
        if (page == Page.PHOTOGRAPHY) {
            graphics.textWithWordWrap(this.font,
                    Component.literal("Timer runs first; Shader Settle runs after you finish composing. Moving the camera during settle restarts the wait. Shader Settle is best-effort and cannot guarantee every shader pack converges perfectly."),
                    x, y, width, HyperShotTheme.TEXT_MUTED, false);
        } else {
            graphics.textWithWordWrap(this.font,
                    Component.literal("Recommended: tap the configured screenshot key for an instant queued Photo, hold it for the camera viewfinder. The viewfinder itself keeps normal mouselook; F7 opens its cursor-enabled control drawer."),
                    x, y, width, HyperShotTheme.TEXT_MUTED, false);
        }
    }

    private Button addButton(UiLayout.Rect rect, String text, Runnable action) {
        return this.addRenderableWidget(Button.builder(Component.literal(text), b -> action.run())
                .bounds(rect.left(), rect.top(), rect.width(), rect.height()).build());
    }

    private void setPage(Page page) {
        this.page = page;
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
        timerText = Integer.toString(HyperShotClient.config().timerSeconds);
        saveAndRebuild();
    }

    private void cycleGuide() {
        GuideType[] values = GuideType.values();
        HyperShotClient.config().guideType = values[(HyperShotClient.config().guideType.ordinal() + 1) % values.length];
        saveAndRebuild();
    }

    private void cycleGuideOpacity() {
        int percent = Math.round(HyperShotClient.config().guideOpacity * 100);
        percent = percent < 25 ? 25 : percent < 45 ? 45 : percent < 65 ? 65 : percent < 85 ? 85 : percent < 100 ? 100 : 25;
        HyperShotClient.config().guideOpacity = percent / 100.0f;
        saveAndRebuild();
    }

    private void cycleSettle() {
        ShaderSettleProfile profile = HyperShotClient.config().shaderSettleProfile;
        HyperShotClient.config().shaderSettleProfile = switch (profile) {
            case OFF -> ShaderSettleProfile.QUICK;
            case QUICK -> ShaderSettleProfile.STANDARD;
            case STANDARD -> ShaderSettleProfile.DEEP;
            case DEEP -> ShaderSettleProfile.CUSTOM;
            case CUSTOM -> ShaderSettleProfile.OFF;
        };
        saveAndRebuild();
    }

    private void applyCustomValues() {
        try {
            int timer = Integer.parseInt(timerText.trim());
            int settle = Integer.parseInt(settleText.trim());
            HyperShotClient.config().timerSeconds = Math.max(0, Math.min(60, timer));
            HyperShotClient.config().customShaderSettleMs = Math.max(0, Math.min(10_000, settle));
            HyperShotClient.config().shaderSettleProfile = ShaderSettleProfile.CUSTOM;
            timerText = Integer.toString(HyperShotClient.config().timerSeconds);
            settleText = Integer.toString(HyperShotClient.config().customShaderSettleMs);
            saveAndRebuild();
        } catch (NumberFormatException error) {
            HyperShotClient.reportUiError("Invalid camera timing", new IllegalArgumentException("Timer must be 0-60 seconds and shader settle 0-10000 ms"));
        }
    }

    private void cycleF2Behavior() {
        F2Behavior[] values = F2Behavior.values();
        HyperShotClient.config().f2Behavior = values[(HyperShotClient.config().f2Behavior.ordinal() + 1) % values.length];
        saveAndRebuild();
    }

    private void cycleHoldThreshold() {
        int value = HyperShotClient.config().f2HoldThresholdMs;
        HyperShotClient.config().f2HoldThresholdMs = value < 250 ? 250 : value < 350 ? 350 : value < 500 ? 500 : value < 750 ? 750 : 250;
        saveAndRebuild();
    }

    private void toggleOverlayFade() {
        HyperShotClient.config().cameraOverlayFade = !HyperShotClient.config().cameraOverlayFade;
        saveAndRebuild();
    }

    private void toggleCountdownSounds() {
        HyperShotClient.config().countdownSounds = !HyperShotClient.config().countdownSounds;
        saveAndRebuild();
    }

    private void restoreRecommended() {
        HyperShotClient.config().f2Behavior = F2Behavior.TAP_INSTANT_HOLD_VIEWFINDER;
        HyperShotClient.config().f2HoldThresholdMs = 350;
        HyperShotClient.config().timerSeconds = 0;
        HyperShotClient.config().guideType = GuideType.RULE_OF_THIRDS;
        HyperShotClient.config().guideOpacity = 0.45f;
        HyperShotClient.config().shaderSettleProfile = ShaderSettleProfile.STANDARD;
        HyperShotClient.config().customShaderSettleMs = 1_000;
        HyperShotClient.config().cameraOverlayFade = true;
        HyperShotClient.config().countdownSounds = true;
        timerText = "0";
        settleText = "1000";
        saveAndRebuild();
    }

    private void saveAndRebuild() {
        HyperShotClient.saveConfig();
        rebuildWidgets();
    }

    private static String timerLabel() {
        return HyperShotClient.config().timerSeconds == 0 ? "Timer: Off" : "Timer: " + HyperShotClient.config().timerSeconds + " seconds";
    }

    private static String f2Label(F2Behavior behavior) {
        return switch (behavior) {
            case INSTANT_ONLY -> "Instant only";
            case VIEWFINDER_ONLY -> "Viewfinder only";
            case TAP_INSTANT_HOLD_VIEWFINDER -> "Tap photo / Hold camera";
        };
    }

    private enum Page { PHOTOGRAPHY, INTERACTION }
}
