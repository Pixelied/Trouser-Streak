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

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/** Thumbnail-bounded contact sheet for Burst and Time groups. Never decodes full-resolution sequence frames. */
public final class CaptureGroupScreen extends HyperShotScreen {
    private final String groupId;
    private int page;
    private boolean confirmDelete;

    public CaptureGroupScreen(Screen parent, String groupId) {
        super(Component.literal("HyperShot Capture Group"), parent);
        this.groupId = groupId;
    }

    private List<CaptureRecord> records() {
        return HyperShotClient.galleryIndex().all().stream()
                .filter(record -> groupId != null && groupId.equals(record.groupId))
                .sorted(Comparator.comparingInt(record -> record.groupIndex))
                .toList();
    }

    @Override
    protected void init() {
        super.init();
        UiLayout layout = layout(false);
        List<CaptureRecord> records = records();
        UiLayout.Rect inner = layout.content().inset(14);
        int columns = layout.compact() ? 2 : 3;
        int rows = layout.compact() ? 2 : 2;
        int perPage = columns * rows;
        int pages = Math.max(1, (records.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));

        int gap = 8;
        int cellWidth = Math.max(70, (inner.width() - gap * (columns - 1)) / columns);
        int cellHeight = Math.max(64, (inner.height() - gap * (rows - 1)) / rows);
        int start = page * perPage;
        for (int slot = 0; slot < perPage && start + slot < records.size(); slot++) {
            CaptureRecord record = records.get(start + slot);
            int col = slot % columns;
            int row = slot / columns;
            int x = inner.left() + col * (cellWidth + gap);
            int y = inner.top() + row * (cellHeight + gap);
            int buttonY = y + Math.max(38, cellHeight - 22);
            String label = record.groupLabel == null ? (record.groupIndex + "/" + record.groupCount) : record.groupLabel;
            Button open = this.addRenderableWidget(Button.builder(Component.literal(label), b -> HyperShotClient.openViewer(record))
                    .bounds(x + 4, buttonY, Math.max(1, cellWidth - 8), 18).build());
            open.active = !record.missing;
        }

        List<UiLayout.Rect> footer = footerButtons(layout, 4);
        Button previous = addFooter(footer.get(0), "Previous", () -> { page--; confirmDelete = false; rebuildWidgets(); });
        previous.active = page > 0;
        Button next = addFooter(footer.get(1), "Next", () -> { page++; confirmDelete = false; rebuildWidgets(); });
        next.active = page + 1 < pages;
        addFooter(footer.get(2), confirmDelete ? "Confirm delete group" : "Delete group", this::deleteGroup);
        addFooter(footer.get(3), "Done", this::onClose);
    }

    private Button addFooter(UiLayout.Rect rect, String label, Runnable action) {
        return this.addRenderableWidget(Button.builder(Component.literal(label), b -> action.run())
                .bounds(rect.left(), rect.top(), rect.width(), rect.height()).build());
    }

    private void deleteGroup() {
        if (!confirmDelete) {
            confirmDelete = true;
            rebuildWidgets();
            return;
        }
        List<CaptureRecord> records = records();
        try {
            for (CaptureRecord record : records) {
                HyperShotClient.galleryFiles().delete(record);
                HyperShotClient.galleryIndex().remove(record.id);
            }
            onClose();
        } catch (IOException error) {
            confirmDelete = false;
            HyperShotClient.reportUiError("Delete capture group failed", error);
            rebuildWidgets();
        }
    }

    @Override
    protected void extractHyperShotBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        UiLayout layout = layout(false);
        List<CaptureRecord> records = records();
        String type = records.isEmpty() || records.getFirst().groupType == null
                ? "Sequence" : HyperShotTheme.titleCase(records.getFirst().groupType);
        boolean complete = records.stream().allMatch(CaptureRecord::isGroupComplete)
                && !records.isEmpty() && records.size() == records.getFirst().groupCount;
        drawChrome(graphics, layout, Component.literal(type + " • " + records.size() + " frames • " + (complete ? "Complete" : "Incomplete")));
        drawContentPanels(graphics, layout);
        if (records.isEmpty()) {
            graphics.centeredText(this.font, "This capture group no longer has any frames.", layout.content().centerX(), layout.content().centerY(), HyperShotTheme.TEXT_MUTED);
            return;
        }

        UiLayout.Rect inner = layout.content().inset(14);
        int columns = layout.compact() ? 2 : 3;
        int rows = 2;
        int perPage = columns * rows;
        int pages = Math.max(1, (records.size() + perPage - 1) / perPage);
        int gap = 8;
        int cellWidth = Math.max(70, (inner.width() - gap * (columns - 1)) / columns);
        int cellHeight = Math.max(64, (inner.height() - gap * (rows - 1)) / rows);
        int start = page * perPage;

        for (int slot = 0; slot < perPage && start + slot < records.size(); slot++) {
            CaptureRecord record = records.get(start + slot);
            int col = slot % columns;
            int row = slot / columns;
            int x = inner.left() + col * (cellWidth + gap);
            int y = inner.top() + row * (cellHeight + gap);
            int previewHeight = Math.max(30, cellHeight - 28);
            UiLayout.Rect cell = new UiLayout.Rect(x, y, cellWidth, previewHeight);
            HyperShotTheme.raisedPanel(graphics, cell);
            var fit = ThumbnailGenerator.fit(record.width, record.height, Math.max(1, cellWidth - 8), Math.max(1, previewHeight - 8));
            int px = cell.centerX() - fit.width() / 2;
            int py = cell.centerY() - fit.height() / 2;
            Identifier texture = HyperShotClient.thumbnailTextures().get(record.thumbnail());
            if (texture != null) {
                var source = ThumbnailGenerator.fit(record.width, record.height, 480, 270);
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture, px, py, 0, 0, fit.width(), fit.height(), source.width(), source.height());
            } else {
                graphics.fill(px, py, px + fit.width(), py + fit.height(), HyperShotTheme.PANEL);
                graphics.centeredText(this.font, "Loading…", cell.centerX(), cell.centerY(), HyperShotTheme.TEXT_MUTED);
            }
            graphics.text(this.font, record.groupIndex + "/" + record.groupCount,
                    cell.left() + 6, cell.top() + 6, HyperShotTheme.TEXT, true);
            if (record.favorite) graphics.text(this.font, "★", cell.right() - 14, cell.top() + 6, HyperShotTheme.WARNING, true);
        }
        graphics.text(this.font, "Page " + (page + 1) + " / " + pages, inner.left(), inner.bottom() - 10, HyperShotTheme.TEXT_DIM, false);
    }
}
