package dev.hypershot.ui;

import dev.hypershot.HyperShotClient;
import dev.hypershot.gallery.CaptureRecord;
import dev.hypershot.gallery.GalleryFileService;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class GalleryScreen extends HyperShotScreen {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());
    private static final int HORIZONTAL_MARGIN = 12;
    private static final int CONTROL_GAP = 4;
    private String query = "";
    private boolean favoritesOnly;
    private int page;
    private CaptureRecord selected;
    private GalleryFileService.DeletedCapture undo;

    public GalleryScreen(Screen parent) { super(Component.translatable("screen.hypershot.gallery.title"), parent); }

    @Override
    protected void init() {
        super.init();
        int contentWidth = Math.min(720, Math.max(1, this.width - HORIZONTAL_MARGIN * 2));
        int left = (this.width - contentWidth) / 2;
        boolean compact = contentWidth < 360;

        int searchButtonWidth = compact ? 58 : 72;
        int favoritesWidth = compact ? 70 : 84;
        int searchWidth = Math.max(40, contentWidth - searchButtonWidth - favoritesWidth - CONTROL_GAP * 2);
        int searchButtonX = left + searchWidth + CONTROL_GAP;
        int favoritesX = searchButtonX + searchButtonWidth + CONTROL_GAP;

        EditBox search = new EditBox(this.font, left, 44, searchWidth, 20, Component.literal("Search screenshots"));
        search.setValue(query);
        search.setMaxLength(128);
        search.setResponder(value -> query = value);
        this.addRenderableWidget(search);
        this.addRenderableWidget(Button.builder(Component.literal(compact ? "Go" : "Search"), b -> {
            page = 0;
            rebuildWidgets();
        }).bounds(searchButtonX, 44, searchButtonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(compact
                        ? (favoritesOnly ? "★ Only" : "☆ All")
                        : (favoritesOnly ? "★ Favorites" : "☆ Favorites")), b -> {
            favoritesOnly = !favoritesOnly;
            page = 0;
            rebuildWidgets();
        }).bounds(favoritesX, 44, favoritesWidth, 20).build());

        List<CaptureRecord> results = HyperShotClient.galleryIndex().search(query, favoritesOnly);
        int pageControlsY = this.height - 82;
        int actionY = this.height - 56;
        int rows = Math.max(1, (pageControlsY - 76 - 4) / 24);
        int pages = Math.max(1, (results.size() + rows - 1) / rows);
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * rows;
        int end = Math.min(results.size(), start + rows);
        int y = 76;
        for (int i = start; i < end; i++) {
            CaptureRecord record = results.get(i);
            String marker = record.favorite ? "★ " : "";
            String missing = record.missing ? " [missing]" : "";
            String label = marker + record.filename + " — " + record.width + "×" + record.height + " — " + TIME.format(record.timestamp()) + missing;
            this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
                selected = record;
                rebuildWidgets();
            }).bounds(left, y, contentWidth, 20).build());
            y += 24;
        }

        Button previous = this.addRenderableWidget(Button.builder(Component.literal("‹"), b -> {
            page--;
            rebuildWidgets();
        }).bounds(left, pageControlsY, 32, 20).build());
        previous.active = page > 0;
        Button pageLabel = this.addRenderableWidget(Button.builder(Component.literal((page + 1) + " / " + pages), b -> {})
                .bounds(left + 36, pageControlsY, 70, 20).build());
        pageLabel.active = false;
        Button next = this.addRenderableWidget(Button.builder(Component.literal("›"), b -> {
            page++;
            rebuildWidgets();
        }).bounds(left + 110, pageControlsY, 32, 20).build());
        next.active = page + 1 < pages;

        int actionCount = 5;
        int actionWidth = Math.max(24, (contentWidth - CONTROL_GAP * (actionCount - 1)) / actionCount);
        int usedWidth = actionWidth * actionCount + CONTROL_GAP * (actionCount - 1);
        int actionX = left + Math.max(0, (contentWidth - usedWidth) / 2);
        Button preview = this.addRenderableWidget(Button.builder(Component.literal(compact ? "View" : "Preview"), b -> {
            if (selected != null) HyperShotClient.openViewer(selected);
        }).bounds(actionX, actionY, actionWidth, 20).build());
        Button reveal = this.addRenderableWidget(Button.builder(Component.literal(compact ? "Files" : "Reveal"), b -> {
            if (selected != null) HyperShotClient.platform().reveal(selected.image());
        }).bounds(actionX + (actionWidth + CONTROL_GAP), actionY, actionWidth, 20).build());
        Button favorite = this.addRenderableWidget(Button.builder(Component.literal(selected != null && selected.favorite ? "★" : "☆"), b -> toggleFavorite())
                .bounds(actionX + (actionWidth + CONTROL_GAP) * 2, actionY, actionWidth, 20).build());
        Button delete = this.addRenderableWidget(Button.builder(Component.literal(compact ? "Del" : "Delete"), b -> deleteSelected())
                .bounds(actionX + (actionWidth + CONTROL_GAP) * 3, actionY, actionWidth, 20).build());
        Button undoButton = this.addRenderableWidget(Button.builder(Component.literal("Undo"), b -> undoDelete())
                .bounds(actionX + (actionWidth + CONTROL_GAP) * 4, actionY, actionWidth, 20).build());
        preview.active = selected != null && !selected.missing;
        reveal.active = selected != null && !selected.missing;
        favorite.active = selected != null;
        delete.active = selected != null;
        undoButton.active = undo != null;

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        drawHeader(graphics);
        List<CaptureRecord> results = HyperShotClient.galleryIndex().search(query, favoritesOnly);
        graphics.text(this.font, results.size() + (results.size() == 1 ? " capture" : " captures"), 16, 23, 0xFF8E97A6, false);
        if (results.isEmpty()) {
            graphics.centeredText(this.font, "No screenshots match this search.", this.width / 2, this.height / 2, 0xFF9AA2AE);
        }
        if (selected != null && this.width >= 520) {
            String selectedText = "Selected: " + selected.filename;
            graphics.text(this.font, selectedText, this.width - 16 - this.font.width(selectedText), 23, 0xFF8E97A6, false);
        }
    }
}
