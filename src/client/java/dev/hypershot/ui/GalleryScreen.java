package dev.hypershot.ui;

import dev.hypershot.HyperShotClient;
import dev.hypershot.core.ThumbnailGenerator;
import dev.hypershot.core.UiLayout;
import dev.hypershot.gallery.CaptureRecord;
import dev.hypershot.gallery.GalleryFileService;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class GalleryScreen extends HyperShotScreen {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault());
    private String query = "";
    private boolean favoritesOnly;
    private int page;
    private CaptureRecord selected;
    private GalleryFileService.DeletedCapture undo;

    public GalleryScreen(Screen parent) {
        super(Component.translatable("screen.hypershot.gallery.title"), parent);
    }

    @Override
    protected void init() {
        super.init();
        UiLayout layout = layout(false);
        int innerLeft = contentLeft(layout);
        int innerWidth = layout.content().width() - 28;
        boolean showDetails = innerWidth >= 620;
        int listWidth = showDetails ? Math.max(320, (innerWidth * 3) / 5) : innerWidth;
        int searchY = layout.content().top() + 12;

        EditBox search = new EditBox(this.font, innerLeft, searchY, Math.max(80, listWidth - 174), 20,
                Component.literal("Search screenshots"));
        search.setValue(query);
        search.setMaxLength(128);
        search.setResponder(value -> query = value);
        this.addRenderableWidget(search);
        this.addRenderableWidget(Button.builder(Component.literal("Search"), b -> {
            page = 0;
            rebuildWidgets();
        }).bounds(innerLeft + listWidth - 166, searchY, 68, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(favoritesOnly ? "Favorites only" : "All captures"), b -> {
            favoritesOnly = !favoritesOnly;
            page = 0;
            rebuildWidgets();
        }).bounds(innerLeft + listWidth - 92, searchY, 92, 20).build());

        List<CaptureRecord> results = HyperShotClient.galleryIndex().search(query, favoritesOnly);
        int listTop = searchY + 32;
        int listBottom = layout.content().bottom() - (layout.compact() ? 66 : 28);
        int rows = Math.max(2, (listBottom - listTop) / 25);
        int pages = Math.max(1, (results.size() + rows - 1) / rows);
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * rows;
        int end = Math.min(results.size(), start + rows);
        int y = listTop;
        for (int i = start; i < end; i++) {
            CaptureRecord record = results.get(i);
            String state = record.missing ? "MISSING" : record.format;
            String marker = selected != null && selected.id.equals(record.id) ? "▶ " : (record.favorite ? "★ " : "");
            String label = marker + record.filename + "  •  " + record.width + "×" + record.height + "  •  " + state;
            this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
                selected = record;
                rebuildWidgets();
            }).bounds(innerLeft, y, listWidth, 21).build());
            y += 25;
        }

        if (layout.compact()) {
            List<UiLayout.Rect> footer = footerButtons(layout, 4);
            Button previous = addAction(footer.get(0), "Prev", () -> { page--; rebuildWidgets(); });
            previous.active = page > 0;
            Button next = addAction(footer.get(1), "Next", () -> { page++; rebuildWidgets(); });
            next.active = page + 1 < pages;
            Button preview = addAction(footer.get(2), "Preview", () -> { if (selected != null) HyperShotClient.openViewer(selected); });
            preview.active = selected != null && !selected.missing;
            addAction(footer.get(3), "Done", this::onClose);

            UiLayout.Rect compactRow = new UiLayout.Rect(innerLeft, layout.content().bottom() - 30, listWidth, 20);
            List<UiLayout.Rect> actions = UiLayout.distribute(compactRow, 3, 4);
            Button favorite = addAction(actions.get(0), selected != null && selected.favorite ? "Unfav" : "Favorite", this::toggleFavorite);
            favorite.active = selected != null;
            Button delete = addAction(actions.get(1), "Delete", this::deleteSelected);
            delete.active = selected != null;
            Button undoButton = addAction(actions.get(2), "Undo", this::undoDelete);
            undoButton.active = undo != null;
        } else {
            List<UiLayout.Rect> footer = footerButtons(layout, 8);
            Button previous = addAction(footer.get(0), "Previous", () -> { page--; rebuildWidgets(); });
            previous.active = page > 0;
            Button next = addAction(footer.get(1), "Next", () -> { page++; rebuildWidgets(); });
            next.active = page + 1 < pages;
            Button preview = addAction(footer.get(2), "Preview", () -> { if (selected != null) HyperShotClient.openViewer(selected); });
            preview.active = selected != null && !selected.missing;
            Button reveal = addAction(footer.get(3), "Reveal", () -> { if (selected != null) HyperShotClient.platform().reveal(selected.image()); });
            reveal.active = selected != null && !selected.missing;
            Button favorite = addAction(footer.get(4), selected != null && selected.favorite ? "Unfav" : "Favorite", this::toggleFavorite);
            favorite.active = selected != null;
            Button delete = addAction(footer.get(5), "Delete", this::deleteSelected);
            delete.active = selected != null;
            Button undoButton = addAction(footer.get(6), "Undo", this::undoDelete);
            undoButton.active = undo != null;
            addAction(footer.get(7), "Done", this::onClose);
        }
    }

    private Button addAction(UiLayout.Rect rect, String label, Runnable action) {
        return this.addRenderableWidget(Button.builder(Component.literal(label), b -> action.run())
                .bounds(rect.left(), rect.top(), rect.width(), rect.height()).build());
    }

    private void toggleFavorite() {
        if (selected == null) return;
        selected.favorite = !selected.favorite;
        HyperShotClient.galleryIndex().setFavorite(selected.id, selected.favorite);
        rebuildWidgets();
    }

    private void deleteSelected() {
        if (selected == null) return;
        try {
            undo = HyperShotClient.galleryFiles().delete(selected);
            HyperShotClient.galleryIndex().remove(selected.id);
            selected = null;
            rebuildWidgets();
        } catch (IOException error) {
            HyperShotClient.reportUiError("Delete failed", error);
        }
    }

    private void undoDelete() {
        if (undo == null) return;
        try {
            HyperShotClient.galleryFiles().restore(undo);
            HyperShotClient.galleryIndex().add(undo.record());
            selected = undo.record();
            undo = null;
            rebuildWidgets();
        } catch (IOException error) {
            HyperShotClient.reportUiError("Undo failed", error);
        }
    }

    @Override
    protected void extractHyperShotBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        UiLayout layout = layout(false);
        List<CaptureRecord> results = HyperShotClient.galleryIndex().search(query, favoritesOnly);
        drawChrome(graphics, layout, Component.literal(results.size() + (results.size() == 1 ? " capture" : " captures")));
        drawContentPanels(graphics, layout);

        int innerLeft = contentLeft(layout);
        int innerWidth = layout.content().width() - 28;
        boolean showDetails = innerWidth >= 620;
        int listWidth = showDetails ? Math.max(320, (innerWidth * 3) / 5) : innerWidth;
        int visibleRows = Math.max(2, (layout.content().height() - (layout.compact() ? 86 : 58)) / 25);
        int visiblePages = Math.max(1, (results.size() + visibleRows - 1) / visibleRows);
        graphics.text(this.font, "Page " + (page + 1) + " / " + visiblePages, innerLeft,
                layout.content().bottom() - (layout.compact() ? 40 : 12), HyperShotTheme.TEXT_DIM, false);
        if (showDetails) {
            int detailsX = innerLeft + listWidth + 10;
            UiLayout.Rect details = new UiLayout.Rect(detailsX, layout.content().top() + 12,
                    Math.max(1, innerLeft + innerWidth - detailsX), layout.content().height() - 24);
            HyperShotTheme.raisedPanel(graphics, details);
            if (selected == null) {
                graphics.centeredText(this.font, "Select a capture to inspect it", details.centerX(), details.centerY(), HyperShotTheme.TEXT_MUTED);
            } else {
                drawDetails(graphics, selected, details);
            }
        } else if (selected != null) {
            graphics.text(this.font, "Selected: " + selected.filename, innerLeft, layout.content().bottom() - (layout.compact() ? 56 : 12),
                    HyperShotTheme.TEXT_MUTED, false);
        }

        if (results.isEmpty()) {
            graphics.centeredText(this.font, "No screenshots match this search.", innerLeft + listWidth / 2,
                    layout.content().centerY(), HyperShotTheme.TEXT_MUTED);
        }
    }

    private void drawDetails(GuiGraphicsExtractor graphics, CaptureRecord record, UiLayout.Rect details) {
        int x = details.left() + 12;
        int width = details.width() - 24;
        int previewHeight = Math.min(150, Math.max(60, details.height() / 2));
        var fit = ThumbnailGenerator.fit(record.width, record.height, width, previewHeight);
        int previewX = details.centerX() - fit.width() / 2;
        int previewY = details.top() + 12;
        graphics.fill(previewX - 1, previewY - 1, previewX + fit.width() + 1, previewY + fit.height() + 1, HyperShotTheme.BORDER);
        Identifier texture = HyperShotClient.thumbnailTextures().get(record.thumbnail());
        if (texture != null) {
            var source = ThumbnailGenerator.fit(record.width, record.height, 480, 270);
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, previewX, previewY, 0, 0,
                    fit.width(), fit.height(), source.width(), source.height());
        } else {
            graphics.fill(previewX, previewY, previewX + fit.width(), previewY + fit.height(), HyperShotTheme.PANEL);
            graphics.centeredText(this.font, "Loading preview…", details.centerX(), previewY + fit.height() / 2, HyperShotTheme.TEXT_MUTED);
        }
        int textY = previewY + fit.height() + 14;
        graphics.text(this.font, record.filename, x, textY, HyperShotTheme.TEXT, true);
        graphics.text(this.font, record.width + " × " + record.height + "  •  " + record.format,
                x, textY + 18, HyperShotTheme.TEXT_MUTED, false);
        graphics.text(this.font, HyperShotTheme.humanBytes(record.fileSize) + "  •  " + TIME.format(record.timestamp()),
                x, textY + 34, HyperShotTheme.TEXT_MUTED, false);
        graphics.text(this.font, "Preset: " + record.preset, x, textY + 56, HyperShotTheme.TEXT_MUTED, false);
        graphics.text(this.font, "Mode: " + HyperShotTheme.titleCase(record.captureMode), x, textY + 72, HyperShotTheme.TEXT_MUTED, false);
        graphics.text(this.font, record.favorite ? "★ Favorite" : "Not favorited", x, textY + 94,
                record.favorite ? HyperShotTheme.WARNING : HyperShotTheme.TEXT_DIM, false);
    }
}
