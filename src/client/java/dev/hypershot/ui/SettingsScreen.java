package dev.hypershot.ui;

import dev.hypershot.HyperShotClient;
import dev.hypershot.config.CapturePreset;
import dev.hypershot.config.MetadataPrivacy;
import dev.hypershot.core.OutputFormat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends HyperShotScreen {
    private static final int GAP = 8;

    public SettingsScreen(Screen parent) { super(Component.translatable("screen.hypershot.settings.title"), parent); }

    @Override
    protected void init() {
        super.init();
        int contentWidth = Math.min(620, Math.max(1, this.width - 24));
        int left = (this.width - contentWidth) / 2;
        int columnWidth = Math.max(72, (contentWidth - GAP) / 2);
        int right = left + columnWidth + GAP;
        int y = 50;
        CapturePreset preset = HyperShotClient.config().activePreset();

        addToggle(left, y, columnWidth, "Replace vanilla F2", HyperShotClient.config().replaceVanillaF2,
                value -> HyperShotClient.config().replaceVanillaF2 = value);
        addToggle(right, y, columnWidth, "Screenshot notifications", HyperShotClient.config().notificationsEnabled,
                value -> HyperShotClient.config().notificationsEnabled = value);
        y += 26;

        addToggle(left, y, columnWidth, "Hide HUD in active preset", preset.hideHud, value -> preset.hideHud = value);
        addToggle(right, y, columnWidth, "Hide hand in active preset", preset.hideHand, value -> preset.hideHand = value);
        y += 26;

        addToggle(left, y, columnWidth, "Hide selection outline", preset.hideBlockOutline, value -> preset.hideBlockOutline = value);
        addToggle(right, y, columnWidth, "Write JSON metadata", preset.includeMetadata, value -> preset.includeMetadata = value);
        y += 26;

        this.addRenderableWidget(Button.builder(Component.literal("Output: " + preset.outputFormat.name()), b -> {
            preset.outputFormat = preset.outputFormat == OutputFormat.PNG ? OutputFormat.JPEG : OutputFormat.PNG;
            persistAndRebuild();
        }).bounds(left, y, columnWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Metadata: " + HyperShotClient.config().metadataPrivacy.name()), b -> {
            MetadataPrivacy[] values = MetadataPrivacy.values();
            int next = (HyperShotClient.config().metadataPrivacy.ordinal() + 1) % values.length;
            HyperShotClient.config().metadataPrivacy = values[next];
            persistAndRebuild();
        }).bounds(right, y, columnWidth, 20).build());
        y += 26;

        this.addRenderableWidget(Button.builder(Component.literal("Diagnostics"), b -> this.minecraft.gui.setScreen(new DiagnosticsScreen(this)))
                .bounds(left, y, columnWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Open HyperShot folder"), b -> HyperShotClient.platform().open(HyperShotClient.paths().root()))
                .bounds(right, y, columnWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    private void addToggle(int x, int y, int width, String label, boolean value, BooleanSetter setter) {
        this.addRenderableWidget(Button.builder(Component.literal((value ? "✓ " : "✕ ") + label), b -> {
            setter.set(!value);
            persistAndRebuild();
        }).bounds(x, y, width, 20).build());
    }

    private void persistAndRebuild() {
        HyperShotClient.saveConfig();
        rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        drawHeader(graphics);
        CapturePreset preset = HyperShotClient.config().activePreset();
        graphics.text(this.font, "Active preset: " + preset.name, 16, 23, 0xFF8E97A6, false);
        if (this.height >= 220) {
            int textWidth = Math.min(620, Math.max(1, this.width - 24));
            graphics.textWithWordWrap(this.font,
                    Component.literal("Every control here changes real capture behavior. Unsupported modes stay hidden."),
                    (this.width - textWidth) / 2, this.height - 52, textWidth, 0xFF8E97A6, false);
        }
    }

    @FunctionalInterface private interface BooleanSetter { void set(boolean value); }
}
