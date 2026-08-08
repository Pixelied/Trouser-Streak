package dev.hypershot.ui;

import dev.hypershot.HyperShotClient;
import dev.hypershot.core.UiLayout;
import dev.hypershot.core.camera.CinematicCapabilities;
import dev.hypershot.core.camera.CinematicTimePreset;
import dev.hypershot.core.camera.CinematicWeatherPreset;
import dev.hypershot.core.camera.F2Behavior;
import dev.hypershot.core.camera.GuideType;
import dev.hypershot.core.camera.ShaderSettleProfile;
import dev.hypershot.core.camera.TimeBracketPlan;
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
    private String burstCountText;
    private String burstIntervalText;
    private String timeStartText;
    private String timeEndText;
    private String timeStepText;

    public CameraSettingsScreen(Screen parent) {
        super(Component.literal("HyperShot Camera Behavior"), parent);
    }

    @Override
    protected void init() {
        super.init();
        initializeTextFields();

        UiLayout layout = layout(false);
        int x = contentLeft(layout);
        int available = Math.max(1, layout.content().width() - 28);
        int y = layout.content().top() + 16;
        List<UiLayout.Rect> tabs = UiLayout.distribute(new UiLayout.Rect(x, y, Math.min(680, available), 20), 4, 6);
        addButton(tabs.get(0), (page == Page.PHOTOGRAPHY ? "✓ " : "") + "Photography", () -> setPage(Page.PHOTOGRAPHY));
        addButton(tabs.get(1), (page == Page.SEQUENCES ? "✓ " : "") + "Sequences", () -> setPage(Page.SEQUENCES));
        addButton(tabs.get(2), (page == Page.CINEMATIC ? "✓ " : "") + "Cinematic", () -> setPage(Page.CINEMATIC));
        addButton(tabs.get(3), (page == Page.INTERACTION ? "✓ " : "") + "F2 & Viewfinder", () -> setPage(Page.INTERACTION));
        y += 42;

        switch (page) {
            case PHOTOGRAPHY -> initPhotography(x, available, y);
            case SEQUENCES -> initSequences(x, available, y);
            case CINEMATIC -> initCinematic(x, available, y);
            case INTERACTION -> initInteraction(x, available, y);
        }

        List<UiLayout.Rect> footer = footerButtons(layout, 3);
        Button viewfinder = addButton(footer.get(0), "Open Camera", () -> {
            HyperShotClient.openCameraViewfinder();
            this.minecraft.gui.setScreen(null);
        });
        viewfinder.active = minecraft.level != null;
        addButton(footer.get(1), "Capture Settings", () -> this.minecraft.gui.setScreen(new SettingsScreen(this)));
        addButton(footer.get(2), "Done", this::onClose);
    }

    private void initializeTextFields() {
        if (timerText == null) timerText = Integer.toString(HyperShotClient.config().timerSeconds);
        if (settleText == null) settleText = Integer.toString(HyperShotClient.config().customShaderSettleMs);
        if (burstCountText == null) burstCountText = Integer.toString(HyperShotClient.config().burstFrameCount);
        if (burstIntervalText == null) burstIntervalText = Long.toString(HyperShotClient.config().burstIntervalMs);
        if (timeStartText == null) timeStartText = Long.toString(HyperShotClient.config().timeStartTick);
        if (timeEndText == null) timeEndText = Long.toString(HyperShotClient.config().timeEndTick);
        if (timeStepText == null) timeStepText = Long.toString(HyperShotClient.config().timeStepTicks);
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

        EditBox timer = editBox(x, y, half, 20, "Custom timer seconds", timerText, 2, value -> timerText = value);
        this.addRenderableWidget(timer);
        EditBox settle = editBox(x + half + gap, y, half, 20, "Custom shader settle milliseconds", settleText, 5, value -> settleText = value);
        this.addRenderableWidget(settle);
        y += 26;
        addButton(new UiLayout.Rect(x, y, width, 20), "Apply custom timer / shader settle", this::applyCustomPhotographyValues);
    }

    private void initSequences(int x, int width, int y) {
        int gap = 6;

        List<UiLayout.Rect> row = UiLayout.distribute(new UiLayout.Rect(x, y, width, 20), 2, gap);
        EditBox burstCount = editBox(row.get(0).left(), y, row.get(0).width(), 20, "Burst frames 1-100", burstCountText, 3, value -> burstCountText = value);
        EditBox burstInterval = editBox(row.get(1).left(), y, row.get(1).width(), 20, "Burst interval ms", burstIntervalText, 5, value -> burstIntervalText = value);
        this.addRenderableWidget(burstCount);
        this.addRenderableWidget(burstInterval);
        y += 26;
        addButton(new UiLayout.Rect(x, y, width, 20), "Apply custom Burst", this::applyBurstValues);
        y += 38;

        row = UiLayout.distribute(new UiLayout.Rect(x, y, width, 20), 3, gap);
        EditBox start = editBox(row.get(0).left(), y, row.get(0).width(), 20, "Start tick", timeStartText, 6, value -> timeStartText = value);
        EditBox end = editBox(row.get(1).left(), y, row.get(1).width(), 20, "End tick", timeEndText, 6, value -> timeEndText = value);
        EditBox step = editBox(row.get(2).left(), y, row.get(2).width(), 20, "Step ticks", timeStepText, 6, value -> timeStepText = value);
        this.addRenderableWidget(start);
        this.addRenderableWidget(end);
        this.addRenderableWidget(step);
        y += 26;
        row = UiLayout.distribute(new UiLayout.Rect(x, y, width, 20), 3, gap);
        addButton(row.get(0), HyperShotClient.config().timeUseCuratedSequence ? "✓ Curated 7-state day" : "Custom Time range", this::toggleTimeSequence);
        addButton(row.get(1), (HyperShotClient.config().timeWrapDayBoundary ? "✓ " : "") + "Wrap midnight", this::toggleTimeWrap);
        addButton(row.get(2), (HyperShotClient.config().timeSettleBetweenFrames ? "✓ " : "") + "Settle each state", this::toggleTimeSettle);
        y += 26;
        addButton(new UiLayout.Rect(x, y, width, 20), "Validate & apply custom Time range", this::applyTimeValues);
        y += 34;
        addButton(new UiLayout.Rect(x, y, width, 20), "Restore recommended sequence settings", this::restoreSequenceRecommended);
    }

    private void initCinematic(int x, int width, int y) {
        int gap = 6;
        CinematicCapabilities capabilities = HyperShotClient.shotCoordinator().cinematicCapabilities(minecraft);

        List<UiLayout.Rect> row = UiLayout.distribute(new UiLayout.Rect(x, y, width, 20), 2, gap);
        Button freeze = addButton(row.get(0), capabilities.worldFreeze()
                ? "World freeze: " + onOff(HyperShotClient.config().cinematicWorldFreeze)
                : "World freeze: Server-owned", this::toggleCinematicFreeze);
        freeze.active = capabilities.worldFreeze();
        Button time = addButton(row.get(1), capabilities.authoritativeTime()
                ? "Time: " + cinematicTimeLabel(HyperShotClient.config().cinematicTimePreset)
                : "Time: Server-owned", this::cycleCinematicTime);
        time.active = capabilities.authoritativeTime();
        y += 28;

        row = UiLayout.distribute(new UiLayout.Rect(x, y, width, 20), 2, gap);
        Button weather = addButton(row.get(0), capabilities.authoritativeWeather()
                ? "Weather: " + HyperShotTheme.titleCaseEnum(HyperShotClient.config().cinematicWeatherPreset)
                : "Weather: Server-owned", this::cycleCinematicWeather);
        weather.active = capabilities.authoritativeWeather();
        Button chunks = addButton(row.get(1), (HyperShotClient.config().cinematicWaitForChunks ? "✓ " : "") + "Wait for nearby chunks",
                this::toggleCinematicChunkWait);
        chunks.active = capabilities.chunkReadiness();
        y += 28;

        row = UiLayout.distribute(new UiLayout.Rect(x, y, width, 20), 2, gap);
        Button camera = addButton(row.get(0), (HyperShotClient.config().cinematicCameraLock ? "✓ " : "") + "Lock camera position",
                this::toggleCinematicCameraLock);
        camera.active = capabilities.cameraLock();
        Button fov = addButton(row.get(1), (HyperShotClient.config().cinematicFovLock ? "✓ " : "") + "Lock FOV",
                this::toggleCinematicFovLock);
        fov.active = capabilities.fovLock();
        y += 28;

        row = UiLayout.distribute(new UiLayout.Rect(x, y, width, 20), 2, gap);
        addButton(row.get(0), (HyperShotClient.config().cinematicCleanFrame ? "✓ " : "") + "Clean Frame", this::toggleCinematicCleanFrame);
        addButton(row.get(1), "Chunk timeout: " + (HyperShotClient.config().cinematicChunkTimeoutMs / 1_000) + " seconds",
                this::cycleCinematicChunkTimeout);
        y += 34;

        addButton(new UiLayout.Rect(x, y, width, 20), "Restore recommended Cinematic settings", this::restoreCinematicRecommended);
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

    private EditBox editBox(int x, int y, int width, int height, String hint, String value, int maxLength,
                            java.util.function.Consumer<String> responder) {
        EditBox box = new EditBox(this.font, x, y, width, height, Component.literal(hint));
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setResponder(responder);
        return box;
    }

    @Override
    protected void extractHyperShotBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        UiLayout layout = layout(false);
        String subtitle = switch (page) {
            case PHOTOGRAPHY -> "Photo preparation defaults";
            case SEQUENCES -> "Burst and Time capture planning";
            case CINEMATIC -> "Scene preparation and safe restoration";
            case INTERACTION -> "Fast screenshot interaction";
        };
        drawChrome(graphics, layout, Component.literal(subtitle));
        drawContentPanels(graphics, layout);
        int x = contentLeft(layout);
        int width = Math.max(1, layout.content().width() - 28);
        int y = layout.content().top() + 108;
        CinematicCapabilities capabilities = HyperShotClient.shotCoordinator().cinematicCapabilities(minecraft);
        String help = switch (page) {
            case PHOTOGRAPHY -> "Timer runs first; Shader Settle runs after you finish composing. Moving the camera during settle restarts the wait. Shader Settle is best-effort and cannot guarantee every shader pack converges perfectly.";
            case SEQUENCES -> "Burst captures one image at a time so peak memory stays bounded. Time mode is authoritative only in singleplayer, uses Minecraft's WorldClock system, and restores the exact original clock after the sequence. Custom ranges must land exactly on the end tick.";
            case CINEMATIC -> capabilities.authoritativeTime()
                    ? "Cinematic snapshots original time, weather and freeze state before changing anything. Chunk readiness runs before optional world freeze; Shader Settle runs once after the scene is ready. Success, cancel and failure all enter the same idempotent restoration path."
                    : "On multiplayer, server-owned time/weather/freeze stay untouched. Client-side camera/FOV lock, nearby-chunk readiness and Clean Frame remain available. A chunk timeout blocks until you explicitly capture anyway or cancel.";
            case INTERACTION -> "Recommended: tap the configured screenshot key for an instant Photo, hold it for the camera viewfinder. Inside the viewfinder, a short F2 press is the shutter for the selected mode; F7 opens the cursor-enabled control drawer.";
        };
        graphics.textWithWordWrap(this.font, Component.literal(help), x, y, width, HyperShotTheme.TEXT_MUTED, false);
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
        HyperShotClient.config().timerSeconds = switch (timer) { case 0 -> 3; case 3 -> 5; case 5 -> 10; default -> 0; };
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

    private void applyCustomPhotographyValues() {
        try {
            int timer = Integer.parseInt(timerText.trim());
            int settle = Integer.parseInt(settleText.trim());
            if (timer < 0 || timer > 60 || settle < 0 || settle > 10_000) throw new IllegalArgumentException();
            HyperShotClient.config().timerSeconds = timer;
            HyperShotClient.config().customShaderSettleMs = settle;
            HyperShotClient.config().shaderSettleProfile = ShaderSettleProfile.CUSTOM;
            saveAndRebuild();
        } catch (RuntimeException error) {
            HyperShotClient.reportUiError("Invalid camera timing", new IllegalArgumentException("Timer must be 0-60 seconds and shader settle 0-10000 ms"));
        }
    }

    private void applyBurstValues() {
        try {
            int count = Integer.parseInt(burstCountText.trim());
            long interval = Long.parseLong(burstIntervalText.trim());
            if (count < 1 || count > 100 || interval < 0 || interval > 60_000) throw new IllegalArgumentException();
            HyperShotClient.config().burstFrameCount = count;
            HyperShotClient.config().burstIntervalMs = interval;
            saveAndRebuild();
        } catch (RuntimeException error) {
            HyperShotClient.reportUiError("Invalid Burst sequence", new IllegalArgumentException("Burst frames must be 1-100 and interval 0-60000 ms"));
        }
    }

    private void applyTimeValues() {
        try {
            long start = Long.parseLong(timeStartText.trim());
            long end = Long.parseLong(timeEndText.trim());
            long step = Long.parseLong(timeStepText.trim());
            TimeBracketPlan.custom(start, end, step, HyperShotClient.config().timeWrapDayBoundary);
            HyperShotClient.config().timeStartTick = Math.floorMod(start, 24_000L);
            HyperShotClient.config().timeEndTick = Math.floorMod(end, 24_000L);
            HyperShotClient.config().timeStepTicks = step;
            HyperShotClient.config().timeUseCuratedSequence = false;
            saveAndRebuild();
        } catch (RuntimeException error) {
            HyperShotClient.reportUiError("Invalid Time sequence", new IllegalArgumentException("Use 0-23999 start/end ticks and a positive step that lands exactly on the end time"));
        }
    }

    private void toggleTimeSequence() {
        HyperShotClient.config().timeUseCuratedSequence = !HyperShotClient.config().timeUseCuratedSequence;
        saveAndRebuild();
    }

    private void toggleTimeWrap() {
        HyperShotClient.config().timeWrapDayBoundary = !HyperShotClient.config().timeWrapDayBoundary;
        saveAndRebuild();
    }

    private void toggleTimeSettle() {
        HyperShotClient.config().timeSettleBetweenFrames = !HyperShotClient.config().timeSettleBetweenFrames;
        saveAndRebuild();
    }

    private void restoreSequenceRecommended() {
        HyperShotClient.config().burstFrameCount = 5;
        HyperShotClient.config().burstIntervalMs = 250;
        HyperShotClient.config().timeUseCuratedSequence = true;
        HyperShotClient.config().timeStartTick = 23_000;
        HyperShotClient.config().timeEndTick = 1_000;
        HyperShotClient.config().timeStepTicks = 500;
        HyperShotClient.config().timeWrapDayBoundary = true;
        HyperShotClient.config().timeSettleBetweenFrames = true;
        burstCountText = "5";
        burstIntervalText = "250";
        timeStartText = "23000";
        timeEndText = "1000";
        timeStepText = "500";
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
        HyperShotClient.config().cinematicChunkTimeoutMs = value < 5_000 ? 5_000 : value < 10_000 ? 10_000
                : value < 20_000 ? 20_000 : value < 30_000 ? 30_000 : 5_000;
        saveAndRebuild();
    }

    private void restoreCinematicRecommended() {
        HyperShotClient.config().cinematicWorldFreeze = false;
        HyperShotClient.config().cinematicTimePreset = CinematicTimePreset.CURRENT;
        HyperShotClient.config().cinematicWeatherPreset = CinematicWeatherPreset.CURRENT;
        HyperShotClient.config().cinematicWaitForChunks = true;
        HyperShotClient.config().cinematicChunkTimeoutMs = 10_000;
        HyperShotClient.config().cinematicCameraLock = true;
        HyperShotClient.config().cinematicFovLock = true;
        HyperShotClient.config().cinematicCleanFrame = true;
        saveAndRebuild();
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

    private static String f2Label(F2Behavior behavior) {
        return switch (behavior) {
            case INSTANT_ONLY -> "Instant only";
            case VIEWFINDER_ONLY -> "Viewfinder only";
            case TAP_INSTANT_HOLD_VIEWFINDER -> "Tap photo / Hold camera";
        };
    }

    private enum Page { PHOTOGRAPHY, SEQUENCES, CINEMATIC, INTERACTION }
}
