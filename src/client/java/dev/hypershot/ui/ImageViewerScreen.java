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
    private final CaptureRecord record;

    public ImageViewerScreen(Screen parent, CaptureRecord record) {
        super(Component.translatable("screen.hypershot.viewer.title"), parent);
        this.record = record;
    }

    @Override
    protected void init() {
        super.init();
        int y = this.height - 32;
        int center = this.width / 2;
        this.addRenderableWidget(Button.builder(Component.literal("Open full image"), b -> HyperShotClient.platform().open(record.image())).bounds(center - 210, y, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Reveal"), b -> HyperShotClient.platform().reveal(record.image())).bounds(center - 84, y, 74, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(record.favorite ? "Unfavorite" : "Favorite"), b -> {
            record.favorite = !record.favorite;
            HyperShotClient.galleryIndex().setFavorite(record.id, record.favorite);
            rebuildWidgets();
        }).bounds(center - 4, y, 84, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(center + 86, y, 74, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        drawHeader(graphics);
        int infoWidth = 220;
        int maxWidth = Math.max(1, this.width - infoWidth - 48);
        int maxHeight = Math.max(1, this.height - 92);
        var fit = ThumbnailGenerator.fit(record.width, record.height, maxWidth, maxHeight);
        int x = 16 + (maxWidth - fit.width()) / 2;
        int y = 44 + (maxHeight - fit.height()) / 2;
        graphics.fill(x - 1, y - 1, x + fit.width() + 1, y + fit.height() + 1, 0xFF3A414C);
        Identifier texture = HyperShotClient.thumbnailTextures().get(record.thumbnail());
        if (texture != null) {
            var source = ThumbnailGenerator.fit(record.width, record.height, 480, 270);
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, fit.width(), fit.height(), source.width(), source.height());
        } else {
            graphics.fill(x, y, x + fit.width(), y + fit.height(), 0xFF20252D);
            graphics.centeredText(this.font, "Loading preview…", x + fit.width() / 2, y + fit.height() / 2, 0xFFADB5C2);
        }
        int infoX = this.width - infoWidth;
        graphics.text(this.font, record.filename, infoX, 52, 0xFFFFFFFF, true);
        graphics.text(this.font, record.width + " × " + record.height, infoX, 76, 0xFFB4BDCA, false);
        graphics.text(this.font, record.format + " • " + humanBytes(record.fileSize), infoX, 92, 0xFFB4BDCA, false);
        graphics.text(this.font, "Preset: " + record.preset, infoX, 116, 0xFFB4BDCA, false);
        graphics.text(this.font, "Mode: " + record.captureMode, infoX, 132, 0xFFB4BDCA, false);
        graphics.text(this.font, record.favorite ? "★ Favorite" : "☆ Not favorite", infoX, 156, record.favorite ? 0xFFFFD166 : 0xFF8E97A6, false);
        graphics.textWithWordWrap(this.font, Component.literal("The in-game view uses the bounded thumbnail. Open full image launches the original without decoding it into Minecraft memory."), infoX, 188, infoWidth - 18, 0xFF8E97A6, false);
    }

    private static String humanBytes(long bytes) {
        double value = bytes;
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++; }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }
}
