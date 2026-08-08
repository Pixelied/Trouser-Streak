package dev.hypershot.ui;

import dev.hypershot.HyperShotClient;
import dev.hypershot.capture.CaptureRequest;
import dev.hypershot.config.CaptureMode;
import dev.hypershot.config.CapturePreset;
import dev.hypershot.config.MetadataPrivacy;
import dev.hypershot.core.CapturePreflight;
import dev.hypershot.core.CaptureSafetyState;
import dev.hypershot.core.OutputFormat;
import dev.hypershot.core.UiLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;

public final class SettingsScreen extends HyperShotScreen {
    private boolean advanced;
    private CaptureRequest request;
    private CapturePreflight preflight;
    private String preflightError;
    private String customWidth;
    private String customHeight;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("screen.hypershot.settings.title"), parent);
    }

    @Override
    protected void init() {
        super.init();
        CapturePreset preset = HyperShotClient.config().activePreset();
        if (customWidth == null) customWidth = Integer.toString(preset.width == 0 ? minecraft.gameRenderer.mainRenderTarget().width : preset.width);
        if (customHeight == null) customHeight = Integer.toString(preset.height == 0 ? minecraft.gameRenderer.mainRenderTarget().height : preset.height);
        recalculate();

        UiLayout layout = layout(false);
        int x = contentLeft(layout);
        int width = Math.max(1, layout.content().width() - 28);
        int y = layout.content().top() + 18;

        List<UiLayout.Rect> modeRow = UiLayout.distribute(new UiLayout.Rect(x, y, Math.min(width, 320), 20), 2, 6);
        addButton(modeRow.get(0), Component.literal(advanced ? "Simple" : "✓ Simple"), () -> setAdvanced(false));
        addButton(modeRow.get(1), Component.literal(advanced ? "✓ Advanced" : "Advanced"), () -> setAdvanced(true));
        y += 44;

        if (advanced) {
            initAdvanced(layout, x, width, y);
        } else {
            initSimple(layout, x, width, y);
        }
    }

    private void initSimple(UiLayout layout, int x, int width, int y) {
        int rowHeight = 22;
        int gap = 6;
        boolean compact = width < 660;
        int perRow = compact ? 3 : 6;
        String[] ids = {"vanilla-plus", "4k", "8k", "16k", "32k", "custom"};
        String[] labels = {"Native", "4K", "8K", "16K", "32K", "Custom"};
        for (int start = 0; start < ids.length; start += perRow) {
            int count = Math.min(perRow, ids.length - start);
            List<UiLayout.Rect> row = UiLayout.distribute(new UiLayout.Rect(x, y, width, rowHeight), count, gap);
            for (int i = 0; i < count; i++) {
                int index = start + i;
                boolean selected = HyperShotClient.config().activePresetId.equals(ids[index]);
                String label = (selected ? "✓ " : "") + labels[index];
                addButton(row.get(i), Component.literal(label), () -> selectPreset(ids[index]));
            }
            y += rowHeight + 5;
        }

        y += 18;
        CapturePreset preset = HyperShotClient.config().activePreset();
        List<UiLayout.Rect> formatRow = UiLayout.distribute(new UiLayout.Rect(x, y, width, 22), 2, gap);
        addButton(formatRow.get(0), Component.literal((preset.outputFormat == OutputFormat.PNG ? "✓ " : "") + "PNG — Lossless"),
                () -> setFormat(OutputFormat.PNG));
        addButton(formatRow.get(1), Component.literal((preset.outputFormat == OutputFormat.JPEG ? "✓ " : "") + "JPEG — Smaller, lossy"),
                () -> setFormat(OutputFormat.JPEG));
        y += 58;

        List<UiLayout.Rect> appearance = UiLayout.distribute(new UiLayout.Rect(x, y, width, 22), compact ? 2 : 3, gap);
        addButton(appearance.get(0), Component.literal((preset.hideHud ? "✓ " : "") + "Hide HUD"), () -> toggleAppearance(0));
        addButton(appearance.get(1), Component.literal((preset.hideHand ? "✓ " : "") + "Hide hand"), () -> toggleAppearance(1));
        if (!compact) {
            addButton(appearance.get(2), Component.literal((preset.hideBlockOutline ? "✓ " : "") + "Hide block outline"), () -> toggleAppearance(2));
        } else {
            y += 27;
            addButton(new UiLayout.Rect(x, y, width, 22), Component.literal((preset.hideBlockOutline ? "✓ " : "") + "Hide block outline"), () -> toggleAppearance(2));
        }

        List<UiLayout.Rect> footer = footerButtons(layout, 4);
        Button capture = addButton(footer.get(0), Component.literal("Take Screenshot"), this::capture);
        capture.active = this.minecraft.level != null && preflight != null && preflight.safetyState() != CaptureSafetyState.CANNOT_START;
        addButton(footer.get(1), Component.literal("Gallery"), () -> HyperShotClient.openGallery(this));
        addButton(footer.get(2), Component.literal("Advanced"), () -> setAdvanced(true));
        addButton(footer.get(3), Component.translatable("gui.done"), this::onClose);
    }

    private void initAdvanced(UiLayout layout, int x, int width, int y) {
        CapturePreset preset = HyperShotClient.config().activePreset();
        int gap = 6;
        int half = (width - gap) / 2;

        EditBox widthBox = new EditBox(this.font, x, y, half, 20, Component.literal("Custom width"));
        widthBox.setMaxLength(6);
        widthBox.setValue(customWidth);
        widthBox.setResponder(value -> customWidth = value);
        this.addRenderableWidget(widthBox);
        EditBox heightBox = new EditBox(this.font, x + half + gap, y, half, 20, Component.literal("Custom height"));
        heightBox.setMaxLength(6);
        heightBox.setValue(customHeight);
        heightBox.setResponder(value -> customHeight = value);
        this.addRenderableWidget(heightBox);
        y += 26;
        addButton(new UiLayout.Rect(x, y, width, 20), Component.literal("Apply custom resolution"), this::applyCustomResolution);
        y += 34;

        List<UiLayout.Rect> row = UiLayout.distribute(new UiLayout.Rect(x, y, width, 20), 2, gap);
        addButton(row.get(0), Component.literal("Tile size: " + preset.tileSize + " px"), this::cycleTileSize);
        addButton(row.get(1), Component.literal("Tile overlap: " + preset.overlap + " px"), this::cycleOverlap);
        y += 26;

        row = UiLayout.distribute(new UiLayout.Rect(x, y, width, 20), 2, gap);
        if (preset.outputFormat == OutputFormat.PNG) {
            addButton(row.get(0), Component.literal("PNG save: " + pngEffortLabel(preset.pngCompression)), this::cyclePngEffort);
        } else {
            addButton(row.get(0), Component.literal("JPEG quality: " + Math.round(preset.jpegQuality * 100) + "%"), this::cycleJpegQuality);
        }
        addButton(row.get(1), Component.literal("Format: " + (preset.outputFormat == OutputFormat.PNG ? "PNG — Lossless" : "JPEG — Lossy")),
                () -> setFormat(preset.outputFormat == OutputFormat.PNG ? OutputFormat.JPEG : OutputFormat.PNG));
        y += 26;

        row = UiLayout.distribute(new UiLayout.Rect(x, y, width, 20), 2, gap);
        addButton(row.get(0), Component.literal((preset.includeMetadata ? "✓ " : "") + "Write JSON metadata"), this::toggleMetadata);
        addButton(row.get(1), Component.literal("Privacy: " + HyperShotTheme.titleCaseEnum(HyperShotClient.config().metadataPrivacy)), this::cyclePrivacy);
        y += 26;

        row = UiLayout.distribute(new UiLayout.Rect(x, y, width, 20), 2, gap);
        addButton(row.get(0), Component.literal("Free-disk reserve: " + HyperShotTheme.humanBytes(HyperShotClient.config().freeDiskMarginBytes)), this::cycleDiskReserve);
        addButton(row.get(1), Component.literal("Restore recommended technical settings"), this::restoreRecommended);

        List<UiLayout.Rect> footer = footerButtons(layout, 4);
        addButton(footer.get(0), Component.literal("Diagnostics"), () -> this.minecraft.gui.setScreen(new DiagnosticsScreen(this)));
        addButton(footer.get(1), Component.literal("Gallery"), () -> HyperShotClient.openGallery(this));
        addButton(footer.get(2), Component.literal("Simple"), () -> setAdvanced(false));
        addButton(footer.get(3), Component.translatable("gui.done"), this::onClose);
    }

    @Override
    protected void extractHyperShotBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        UiLayout layout = layout(false);
        CapturePreset preset = HyperShotClient.config().activePreset();
        drawChrome(graphics, layout, Component.literal(advanced ? "Advanced controls" : "Simple screenshot setup"));
        drawContentPanels(graphics, layout);

        int x = contentLeft(layout);
        int width = Math.max(1, layout.content().width() - 28);
        int y = layout.content().top() + 50;
        graphics.text(this.font, advanced ? "CUSTOM RESOLUTION" : "RESOLUTION", x, y, HyperShotTheme.TEXT_DIM, false);
        if (!advanced) {
            int presetRows = width < 660 ? 2 : 1;
            y += 18 + presetRows * 27;
            graphics.text(this.font, "IMAGE TYPE", x, y, HyperShotTheme.TEXT_DIM, false);
            y += 58;
            graphics.text(this.font, "SCREENSHOT APPEARANCE", x, y, HyperShotTheme.TEXT_DIM, false);
        } else {
            y += 76;
            graphics.text(this.font, "ADVANCED CAPTURE OPTIONS", x, y, HyperShotTheme.TEXT_DIM, false);
            y += 82;
            String qualityHelp = preset.outputFormat == OutputFormat.PNG
                    ? "PNG is lossless. Fast/Balanced/Smallest changes save time and file size, not image quality."
                    : "JPEG is lossy. Lower quality permanently discards image detail; use PNG for maximum quality.";
            graphics.textWithWordWrap(this.font, Component.literal(qualityHelp), x, y, width, HyperShotTheme.TEXT_MUTED, false);
        }

        drawEstimate(graphics, layout, preset);
    }

    private void drawEstimate(GuiGraphicsExtractor graphics, UiLayout layout, CapturePreset preset) {
        int x = contentLeft(layout);
        int width = Math.max(1, layout.content().width() - 28);
        int bottom = layout.content().bottom() - 12;
        int height = Math.min(116, Math.max(72, layout.content().height() / 3));
        int top = bottom - height;
        UiLayout.Rect card = new UiLayout.Rect(x, top, width, height);
        HyperShotTheme.raisedPanel(graphics, card);
        HyperShotTheme.accentBar(graphics, card);
        int tx = card.left() + 12;
        int ty = card.top() + 10;
        graphics.text(this.font, "CAPTURE ESTIMATE", tx, ty, HyperShotTheme.TEXT_DIM, false);
        if (request != null) {
            double megapixels = request.resolution().width() * (double) request.resolution().height() / 1_000_000.0;
            graphics.text(this.font, request.resolution().width() + " × " + request.resolution().height()
                    + "  •  " + String.format(java.util.Locale.ROOT, "%.1f MP", megapixels), tx, ty + 18, HyperShotTheme.TEXT, true);
        }
        if (preflight != null) {
            graphics.text(this.font, preflight.tileCount() + " tiles  •  Temp " + HyperShotTheme.humanBytes(preflight.estimate().temporaryBytes())
                    + "  •  Final ~" + HyperShotTheme.humanBytes(preflight.estimate().estimatedFinalBytes()), tx, ty + 38, HyperShotTheme.TEXT_MUTED, false);
            graphics.text(this.font, safetyLabel(preflight.safetyState()), tx, ty + 60, safetyColor(preflight.safetyState()), true);
            graphics.textWithWordWrap(this.font, Component.literal(preflight.reason()), tx, ty + 76, Math.max(1, card.width() - 24), HyperShotTheme.TEXT_MUTED, false);
        } else {
            graphics.text(this.font, preflightError == null ? "Calculating hardware requirements…" : preflightError,
                    tx, ty + 38, HyperShotTheme.ERROR, false);
        }
        if ("32k".equals(HyperShotClient.config().activePresetId)) {
            graphics.text(this.font, "32K is experimental until a full 30,720 × 17,280 hardware capture is verified.",
                    tx, card.bottom() - 16, HyperShotTheme.WARNING, false);
        }
    }

    private void recalculate() {
        CapturePreset preset = HyperShotClient.config().activePreset();
        int nativeWidth = minecraft.gameRenderer.mainRenderTarget().width;
        int nativeHeight = minecraft.gameRenderer.mainRenderTarget().height;
        request = CaptureRequest.from(preset, nativeWidth, nativeHeight);
        try {
            preflight = HyperShotClient.captureManager().preflight(request);
            preflightError = null;
        } catch (IOException | RuntimeException error) {
            preflight = null;
            preflightError = "Estimate unavailable: " + String.valueOf(error.getMessage());
        }
    }

    private Button addButton(UiLayout.Rect rect, Component label, Runnable action) {
        return this.addRenderableWidget(Button.builder(label, b -> action.run())
                .bounds(rect.left(), rect.top(), rect.width(), rect.height()).build());
    }

    private void selectPreset(String id) {
        if ("custom".equals(id)) {
            CapturePreset custom = ensureCustomPreset();
            customWidth = Integer.toString(custom.width);
            customHeight = Integer.toString(custom.height);
        }
        HyperShotClient.config().activePresetId = id;
        persistAndRebuild();
    }

    private CapturePreset ensureCustomPreset() {
        try {
            return HyperShotClient.config().preset("custom");
        } catch (IllegalArgumentException missing) {
            CapturePreset custom = new CapturePreset("custom", "Custom", CaptureMode.TILED, 3840, 2160, 2048, 32, 6,
                    true, true, true, true);
            HyperShotClient.config().presets.add(custom);
            return custom;
        }
    }

    private void applyCustomResolution() {
        try {
            int width = Integer.parseInt(customWidth.trim());
            int height = Integer.parseInt(customHeight.trim());
            if (width < 64 || height < 64 || width > 100_000 || height > 100_000) {
                throw new IllegalArgumentException("Custom dimensions must be between 64 and 100,000 pixels");
            }
            CapturePreset source = HyperShotClient.config().activePreset();
            CapturePreset custom = ensureCustomPreset();
            custom.width = width;
            custom.height = height;
            custom.mode = CaptureMode.TILED;
            custom.tileSize = source.tileSize;
            custom.overlap = source.overlap;
            custom.pngCompression = source.pngCompression;
            custom.outputFormat = source.outputFormat;
            custom.jpegQuality = source.jpegQuality;
            custom.hideHud = source.hideHud;
            custom.hideHand = source.hideHand;
            custom.hideBlockOutline = source.hideBlockOutline;
            custom.includeMetadata = source.includeMetadata;
            custom.validate();
            HyperShotClient.config().activePresetId = custom.id;
            persistAndRebuild();
        } catch (RuntimeException error) {
            HyperShotClient.reportUiError("Invalid custom resolution", error);
        }
    }

    private void setFormat(OutputFormat format) {
        HyperShotClient.config().activePreset().outputFormat = format;
        persistAndRebuild();
    }

    private void toggleAppearance(int which) {
        CapturePreset preset = HyperShotClient.config().activePreset();
        if (which == 0) preset.hideHud = !preset.hideHud;
        if (which == 1) preset.hideHand = !preset.hideHand;
        if (which == 2) preset.hideBlockOutline = !preset.hideBlockOutline;
        persistAndRebuild();
    }

    private void cycleTileSize() {
        CapturePreset preset = HyperShotClient.config().activePreset();
        preset.tileSize = switch (preset.tileSize) {
            case 1024 -> 2048;
            case 2048 -> 4096;
            default -> 1024;
        };
        if (preset.overlap >= preset.tileSize / 2) preset.overlap = 0;
        persistAndRebuild();
    }

    private void cycleOverlap() {
        CapturePreset preset = HyperShotClient.config().activePreset();
        preset.overlap = switch (preset.overlap) {
            case 0 -> 16;
            case 16 -> 32;
            case 32 -> 64;
            default -> 0;
        };
        persistAndRebuild();
    }

    private void cyclePngEffort() {
        CapturePreset preset = HyperShotClient.config().activePreset();
        preset.pngCompression = preset.pngCompression <= 2 ? 6 : preset.pngCompression <= 7 ? 9 : 1;
        persistAndRebuild();
    }

    private void cycleJpegQuality() {
        CapturePreset preset = HyperShotClient.config().activePreset();
        int quality = Math.round(preset.jpegQuality * 100);
        preset.jpegQuality = switch (quality) {
            case 80 -> 0.90f;
            case 90 -> 0.95f;
            case 95 -> 1.0f;
            default -> 0.80f;
        };
        persistAndRebuild();
    }

    private void toggleMetadata() {
        CapturePreset preset = HyperShotClient.config().activePreset();
        preset.includeMetadata = !preset.includeMetadata;
        persistAndRebuild();
    }

    private void cyclePrivacy() {
        MetadataPrivacy[] values = MetadataPrivacy.values();
        HyperShotClient.config().metadataPrivacy = values[(HyperShotClient.config().metadataPrivacy.ordinal() + 1) % values.length];
        persistAndRebuild();
    }

    private void cycleDiskReserve() {
        long gib = 1024L * 1024 * 1024;
        long current = HyperShotClient.config().freeDiskMarginBytes;
        HyperShotClient.config().freeDiskMarginBytes = current < 2 * gib ? 2 * gib : current < 4 * gib ? 4 * gib : current < 8 * gib ? 8 * gib : gib;
        persistAndRebuild();
    }

    private void restoreRecommended() {
        CapturePreset preset = HyperShotClient.config().activePreset();
        preset.tileSize = 2048;
        preset.overlap = preset.mode == CaptureMode.NATIVE ? 0 : 32;
        preset.pngCompression = 6;
        preset.jpegQuality = 0.92f;
        HyperShotClient.config().freeDiskMarginBytes = 2L * 1024 * 1024 * 1024;
        persistAndRebuild();
    }

    private void setAdvanced(boolean value) {
        advanced = value;
        rebuildWidgets();
    }

    private void persistAndRebuild() {
        HyperShotClient.saveConfig();
        recalculate();
        rebuildWidgets();
    }

    private void capture() {
        HyperShotClient.captureActivePreset();
        this.minecraft.gui.setScreen(parent);
    }

    private static String pngEffortLabel(int level) {
        if (level <= 2) return "Fast save";
        if (level <= 7) return "Balanced — Recommended";
        return "Smallest file";
    }

    private static String safetyLabel(CaptureSafetyState state) {
        return switch (state) {
            case SAFE -> "SAFE";
            case LIKELY_SAFE -> "LIKELY SAFE";
            case HIGH_LOAD -> "HIGH LOAD";
            case NOT_RECOMMENDED -> "NOT RECOMMENDED";
            case CANNOT_START -> "CANNOT START";
        };
    }

    private static int safetyColor(CaptureSafetyState state) {
        return switch (state) {
            case SAFE, LIKELY_SAFE -> HyperShotTheme.SUCCESS;
            case HIGH_LOAD, NOT_RECOMMENDED -> HyperShotTheme.WARNING;
            case CANNOT_START -> HyperShotTheme.ERROR;
        };
    }
}
