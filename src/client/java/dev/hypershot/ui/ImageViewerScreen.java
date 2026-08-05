package dev.hypershot.ui;

import dev.hypershot.HyperShotClient;
import dev.hypershot.core.ThumbnailGenerator;
import dev.hypershot.gallery.CaptureRecord;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Locale;

public final class ImageViewerScreen extends HyperShotScreen {
    private static final int GAP = 4;
    private final CaptureRecord record;

    public ImageViewerScreen(Screen parent, CaptureRecord record) {
        super(Component.translatable("screen.hypershot.viewer.title"), parent);
        this.record = record;
    }

    @Override
    protected void init() {
        super.init();
        int contentWidth = Math.min(520, Math.max(1, this.width - 24));
        int left = (this.width - contentWidth) / 2;
        int buttonWidth = Math.max(38, (contentWidth - GAP * 3) / 4);
        int y = this.height - 30;

        this.addRenderableWidget(Button.builder(Component.literal(buttonWidth < 92 ? "Open" : "Open full image"),
                        b -> HyperShotClient.platform().open(record.image()))
                .bounds(left, y, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(buttonWidth < 64 ? "Files" : "Reveal"),
                        b -> HyperShotClient.platform().reveal(record.image()))
                .bounds(left + buttonWidth + GAP, y, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(record.favorite ? "★" : "☆"), b -> {
            record.favorite = !record.favorite;
            HyperShotClient.galleryIndex().setFavorite(record.id, record.favorite);
            rebuildWidgets();
        }).bounds(left + (buttonWidth + GAP) * 2, y, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(left + (buttonWidth + GAP) * 3, y, buttonWidth, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        drawHeader(graphics);
        boolean wide = this.width >= 520;
        int infoWidth = wide ? Math.min(220, Math.max(160, this.width / 3)) : 0;
        int previewLeft = 12;
        int previewTop = 42;
        int previewWidth = Math.max(1, this.width - 24 - infoWidth - (wide ? 12 : 0));
        int previewBottom = wide ? this.height - 40 : this.height - 88;
        int previewHeight = Math.max(1, previewBottom - previewTop);
        var fit = ThumbnailGenerator.fit(record.width, record.height, previewWidth, previewHeight);
        int x = previewLeft + (previewWidth - fit.width()) / 2;
        int y = previewTop + (previewHeight - fit.height()) / 2;

        graphics.outline(x - 1, y - 1, fit.width() + 2, fit.height() + 2, 0xFF3A414C);
        drawCheckerboard(graphics, x, y, fit.width(), fit.height());
        Identifier texture = HyperShotClient.thumbnailTextures().get(record.thumbnail());
        if (texture != null) {
            var source = ThumbnailGenerator.fit(record.width, record.height, 480, 270);
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0,
                    fit.width(), fit.height(), source.width(), source.height());
        } else {
            graphics.fill(x, y, x + fit.width(), y + fit.height(), 0xCC20252D);
            graphics.centeredText(this.font, "Loading preview…", x + fit.width() / 2, y + fit.height() / 2, 0xFFADB5C2);
        }

        if (wide) drawWideInfo(graphics, this.width - infoWidth, infoWidth);
        else drawCompactInfo(graphics);
    }

    private void drawWideInfo(GuiGraphicsExtractor graphics, int infoX, int infoWidth) {
        graphics.text(this.font, truncate(record.filename, Math.max(12, (infoWidth - 18) / 6)), infoX, 52, 0xFFFFFFFF, true);
        graphics.text(this.font, record.width + " × " + record.height, infoX, 76, 0xFFB4BDCA, false);
        graphics.text(this.font, record.format + " • " + humanBytes(record.fileSize), infoX, 92, 0xFFB4BDCA, false);
        graphics.text(this.font, "Preset: " + truncate(record.preset, 24), infoX, 116, 0xFFB4BDCA, false);
        graphics.text(this.font, "Mode: " + record.captureMode, infoX, 132, 0xFFB4BDCA, false);
        graphics.text(this.font, record.favorite ? "★ Favorite" : "☆ Not favorite", infoX, 156,
                record.favorite ? 0xFFFFD166 : 0xFF8E97A6, false);
        graphics.textWithWordWrap(this.font,
                Component.literal("This view uses a bounded thumbnail. Open loads the original in the system viewer without risking Minecraft's heap."),
                infoX, 184, infoWidth - 18, 0xFF8E97A6, false);
    }

    private void drawCompactInfo(GuiGraphicsExtractor graphics) {
        int y = this.height - 80;
        String first = truncate(record.filename, Math.max(14, (this.width - 24) / 6));
        String second = record.width + " × " + record.height + " • " + record.format + " • " + humanBytes(record.fileSize);
        graphics.centeredText(this.font, first, this.width / 2, y, 0xFFFFFFFF);
        graphics.centeredText(this.font, second, this.width / 2, y + 14, 0xFFB4BDCA);
        graphics.centeredText(this.font,
                record.favorite ? "★ Favorite • bounded preview" : "☆ Bounded preview • open for full image",
                this.width / 2, y + 28, record.favorite ? 0xFFFFD166 : 0xFF8E97A6);
    }

    private static void drawCheckerboard(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int size = 12;
        for (int row = 0; row < height; row += size) {
            for (int column = 0; column < width; column += size) {
                boolean light = ((row / size) + (column / size)) % 2 == 0;
                graphics.fill(x + column, y + row,
                        Math.min(x + width, x + column + size), Math.min(y + height, y + row + size),
                        light ? 0xFF303640 : 0xFF242A32);
            }
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value == null ? "" : value;
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private static String humanBytes(long bytes) {
        double value = bytes;
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++; }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }
}
