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
    private String query = "";
    private boolean favoritesOnly;
    private int page;
    private CaptureRecord selected;
    private GalleryFileService.DeletedCapture undo;

    public GalleryScreen(Screen parent) { super(Component.translatable("screen.hypershot.gallery.title"), parent); }

    @Override
    protected void init() {
        super.init();
        int contentWidth = Math.min(720, this.width - 32);
        int left = (this.width - contentWidth) / 2;
        EditBox search = new EditBox(this.font, left, 44, contentWidth - 170, 20, Component.literal("Search screenshots"));
        search.setValue(query);
        search.setMaxLength(128);
        search.setResponder(value -> query = value);
        this.addRenderableWidget(search);
        this.addRenderableWidget(Button.builder(Component.literal("Search"), b -> { page = 0; rebuildWidgets(); }).bounds(left + contentWidth - 162, 44, 72, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(favoritesOnly ? "★ Favorites" : "☆ Favorites"), b -> {
            favoritesOnly = !favoritesOnly; page = 0; rebuildWidgets();
        }).bounds(left + contentWidth - 84, 44, 84, 20).build());

        List<CaptureRecord> results = HyperShotClient.galleryIndex().search(query, favoritesOnly);
        int rows = Math.max(3, (this.height - 142) / 24);
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
            this.addRenderableWidget(Button.builder(Component.literal(label), b -> { selected = record; rebuildWidgets(); })
                    .bounds(left, y, contentWidth, 20).build());
            y += 24;
        }

        int footerY = this.height - 58;
        this.addRenderableWidget(Button.builder(Component.literal("‹"), b -> { page--; rebuildWidgets(); }).bounds(left, footerY, 32, 20).build()).active = page > 0;
        this.addRenderableWidget(Button.builder(Component.literal((page + 1) + " / " + pages), b -> {}).bounds(left + 38, footerY, 70, 20).build()).active = false;
        this.addRenderableWidget(Button.builder(Component.literal("›"), b -> { page++; rebuildWidgets(); }).bounds(left + 114, footerY, 32, 20).build()).active = page + 1 < pages;

        int actionX = left + contentWidth - 376;
        Button preview = this.addRenderableWidget(Button.builder(Component.literal("Preview"), b -> {
            if (selected != null) HyperShotClient.openViewer(selected);
        }).bounds(actionX, footerY, 68, 20).build());
        Button reveal = this.addRenderableWidget(Button.builder(Component.literal("Reveal"), b -> {
            if (selected != null) HyperShotClient.platform().reveal(selected.image());
        }).bounds(actionX + 72, footerY, 68, 20).build());
        Button favorite = this.addRenderableWidget(Button.builder(Component.literal(selected != null && selected.favorite ? "Unfavorite" : "Favorite"), b -> toggleFavorite())
                .bounds(actionX + 144, footerY, 76, 20).build());
        Button delete = this.addRenderableWidget(Button.builder(Component.literal("Delete"), b -> deleteSelected()).bounds(actionX + 224, footerY, 64, 20).build());
        Button undoButton = this.addRenderableWidget(Button.builder(Component.literal("Undo"), b -> undoDelete()).bounds(actionX + 292, footerY, 64, 20).build());
        preview.active = selected != null && !selected.missing;
        reveal.active = selected != null && !selected.missing;
        favorite.active = selected != null;
        delete.active = selected != null;
        undoButton.active = undo != null;

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
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
        if (results.isEmpty()) graphics.centeredText(this.font, "No screenshots match this search.", this.width / 2, this.height / 2, 0xFF9AA2AE);
        if (selected != null) graphics.text(this.font, "Selected: " + selected.filename, 16, this.height - 27, 0xFF8E97A6, false);
    }
}
