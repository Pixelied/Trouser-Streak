package dev.hypershot.ui;

import dev.hypershot.HyperShotClient;
import dev.hypershot.core.ThumbnailGenerator;
import dev.hypershot.core.UiLayout;
import dev.hypershot.gallery.CaptureRecord;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class ImageViewerScreen extends HyperShotScreen {
    private final CaptureRecord record;

    public ImageViewerScreen(Screen parent, CaptureRecord record) {
        super(Component.translatable("screen.hypershot.viewer.title"), parent);
        this.record = record;
    }

    @Override
    protected void init() {
        super.init();
        UiLayout layout = layout(false);
        var actions = footerButtons(layout, 4);
        UiLayout.Rect open = actions.get(0);
        this.addRenderableWidget(Button.builder(Component.literal(layout.compact() ? "Open" : "Open original"), b -> HyperShotClient.platform().open(record.image()))
                .bounds(open.left(), open.top(), open.width(), open.height()).build());
        UiLayout.Rect reveal = actions.get(1);
        this.addRenderableWidget(Button.builder(Component.literal("Reveal"), b -> HyperShotClient.platform().reveal(record.image()))
                .bounds(reveal.left(), reveal.top(), reveal.width(), reveal.height()).build());
        UiLayout.Rect favorite = actions.get(2);
        this.addRenderableWidget(Button.builder(Component.literal(record.favorite ? (layout.compact() ? "Unfav" : "Unfavorite") : "Favorite"), b -> {
            record.favorite = !record.favorite;
            HyperShotClient.galleryIndex().setFavorite(record.id, record.favorite);
            rebuildWidgets();
        }).bounds(favorite.left(), favorite.top(), favorite.width(), favorite.height()).build());
        UiLayout.Rect done = actions.get(3);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(done.left(), done.top(), done.width(), done.height()).build());
    }

    @Override
    protected void extractHyperShotBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        UiLayout layout = layout(false);
        drawChrome(graphics, layout, Component.literal(record.width + " × " + record.height + " • " + record.format));
        drawContentPanels(graphics, layout);

        UiLayout.Rect inner = layout.content().inset(14);
        boolean sideInfo = inner.width() >= 620;
        int infoWidth = sideInfo ? 230 : 0;
        int imageWidth = sideInfo ? inner.width() - infoWidth - 12 : inner.width();
        int imageHeight = sideInfo ? inner.height() : Math.max(1, inner.height() - 56);
        UiLayout.Rect imageArea = new UiLayout.Rect(inner.left(), inner.top(), imageWidth, imageHeight);
        UiLayout.Rect infoArea = sideInfo
                ? new UiLayout.Rect(imageArea.right() + 12, inner.top(), infoWidth, inner.height())
                : new UiLayout.Rect(inner.left(), imageArea.bottom() + 8, inner.width(), Math.max(1, inner.bottom() - imageArea.bottom() - 8));

        HyperShotTheme.raisedPanel(graphics, imageArea);
        int maxWidth = Math.max(1, imageArea.width() - 16);
        int maxHeight = Math.max(1, imageArea.height() - 16);
        var fit = ThumbnailGenerator.fit(record.width, record.height, maxWidth, maxHeight);
        int x = imageArea.centerX() - fit.width() / 2;
        int y = imageArea.centerY() - fit.height() / 2;
        graphics.fill(x - 1, y - 1, x + fit.width() + 1, y + fit.height() + 1, HyperShotTheme.BORDER);
        Identifier texture = HyperShotClient.thumbnailTextures().get(record.thumbnail());
        if (texture != null) {
            var source = ThumbnailGenerator.fit(record.width, record.height, 480, 270);
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0,
                    fit.width(), fit.height(), source.width(), source.height());
        } else {
            graphics.fill(x, y, x + fit.width(), y + fit.height(), HyperShotTheme.PANEL);
            graphics.centeredText(this.font, "Loading preview…", imageArea.centerX(), imageArea.centerY(), HyperShotTheme.TEXT_MUTED);
        }

        HyperShotTheme.raisedPanel(graphics, infoArea);
        int infoX = infoArea.left() + 12;
        int infoY = infoArea.top() + 12;
        graphics.text(this.font, record.filename, infoX, infoY, HyperShotTheme.TEXT, true);
        graphics.text(this.font, record.width + " × " + record.height, infoX, infoY + 20, HyperShotTheme.TEXT_MUTED, false);
        graphics.text(this.font, record.format + " • " + HyperShotTheme.humanBytes(record.fileSize), infoX, infoY + 36, HyperShotTheme.TEXT_MUTED, false);
        if (sideInfo) {
            HyperShotTheme.divider(graphics, infoX, infoY + 56, infoArea.right() - 12);
            graphics.text(this.font, "Preset", infoX, infoY + 70, HyperShotTheme.TEXT_DIM, false);
            graphics.text(this.font, record.preset, infoX, infoY + 84, HyperShotTheme.TEXT, false);
            graphics.text(this.font, "Capture mode", infoX, infoY + 106, HyperShotTheme.TEXT_DIM, false);
            graphics.text(this.font, HyperShotTheme.titleCase(record.captureMode), infoX, infoY + 120, HyperShotTheme.TEXT, false);
            graphics.text(this.font, record.favorite ? "★ Favorite" : "Not favorited", infoX, infoY + 146,
                    record.favorite ? HyperShotTheme.WARNING : HyperShotTheme.TEXT_DIM, false);
            graphics.textWithWordWrap(this.font,
                    Component.literal("The in-game viewer always uses a bounded thumbnail. Opening the original avoids decoding a gigantic image into Minecraft memory."),
                    infoX, infoY + 174, infoArea.width() - 24, HyperShotTheme.TEXT_MUTED, false);
        }
    }
}
