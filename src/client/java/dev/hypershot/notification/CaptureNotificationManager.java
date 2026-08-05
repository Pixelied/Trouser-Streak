package dev.hypershot.notification;

import dev.hypershot.HyperShotClient;
import dev.hypershot.capture.CaptureListener;
import dev.hypershot.capture.CaptureRequest;
import dev.hypershot.config.HyperShotConfig;
import dev.hypershot.core.CapturePhase;
import dev.hypershot.core.CaptureProgressSnapshot;
import dev.hypershot.core.ThumbnailGenerator;
import dev.hypershot.gallery.CaptureRecord;
import dev.hypershot.gallery.GalleryIndex;
import dev.hypershot.gallery.ThumbnailTextureCache;
import dev.hypershot.platform.PlatformIntegration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** In-game stacked capture cards with real progress and real bounded thumbnails. */
public final class CaptureNotificationManager implements CaptureListener {
    private static final int CARD_WIDTH = 224;
    private static final int CARD_HEIGHT = 76;
    private static final int GAP = 6;
    private final Minecraft minecraft;
    private final HyperShotConfig config;
    private final GalleryIndex gallery;
    private final ThumbnailTextureCache textures;
    private final PlatformIntegration platform;
    private final Deque<Card> cards = new ArrayDeque<>();
    private final Map<String, Card> byId = new HashMap<>();

    public CaptureNotificationManager(Minecraft minecraft, HyperShotConfig config, GalleryIndex gallery,
                                      ThumbnailTextureCache textures, PlatformIntegration platform) {
        this.minecraft = minecraft;
        this.config = config;
        this.gallery = gallery;
        this.textures = textures;
        this.platform = platform;
    }

    @Override
    public synchronized void onStarted(String captureId, CaptureRequest request) {
        Card card = new Card(captureId, request, System.currentTimeMillis());
        cards.addFirst(card);
        byId.put(captureId, card);
        while (cards.size() > 4) remove(cards.removeLast());
    }

    @Override
    public synchronized void onProgress(String captureId, CaptureProgressSnapshot progress) {
        Card card = byId.get(captureId);
        if (card != null) card.progress = progress;
    }

    @Override
    public synchronized void onCompleted(String captureId, Path image, Path metadata, Path thumbnail, long fileSize) {
        Card card = byId.get(captureId);
        if (card == null) return;
        card.image = image;
        card.metadata = metadata;
        card.thumbnail = thumbnail;
        card.fileSize = fileSize;
        card.status = Status.COMPLETE;
        card.completedAt = System.currentTimeMillis();
        card.expiresAt = card.completedAt + config.notificationSeconds * 1000L;
        CaptureRecord record = new CaptureRecord(captureId, image, metadata, thumbnail, card.completedAt,
                card.request.resolution().width(), card.request.resolution().height(), card.request.outputFormat().name(), fileSize,
                card.request.presetName(), card.request.mode().name());
        gallery.add(record);
        textures.get(thumbnail);
    }

    @Override
    public synchronized void onCancelled(String captureId, String reason) {
        Card card = byId.get(captureId);
        if (card == null) return;
        card.status = Status.CANCELLED;
        card.message = reason == null || reason.isBlank() ? "Cancelled" : reason;
        card.completedAt = System.currentTimeMillis();
        card.expiresAt = card.completedAt + 4000L;
    }

    @Override
    public synchronized void onFailed(String captureId, String message, Throwable error) {
        Card card = byId.get(captureId);
        if (card == null) {
            card = new Card(captureId, null, System.currentTimeMillis());
            cards.addFirst(card);
            byId.put(captureId, card);
        }
        card.status = Status.FAILED;
        card.message = message == null ? "Unknown error" : message;
        card.completedAt = System.currentTimeMillis();
        card.expiresAt = card.completedAt + Math.max(8000L, config.notificationSeconds * 1000L);
    }

    public synchronized void extractRenderState(GuiGraphicsExtractor graphics) {
        if (!config.notificationsEnabled || HyperShotClient.captureManager().isRenderingCapturePass()) return;
        long now = System.currentTimeMillis();
        int mouseX = (int) minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
        int mouseY = (int) minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
        int index = 0;
        for (Card card : cards.toArray(Card[]::new)) {
            if (card.expiresAt > 0 && now >= card.expiresAt && !card.bounds.contains(mouseX, mouseY)) {
                remove(card);
                continue;
            }
            double enter = Math.min(1.0, Math.max(0.0, (now - card.createdAt) / 240.0));
            double eased = 1.0 - Math.pow(1.0 - enter, 3.0);
            int targetX = graphics.guiWidth() - CARD_WIDTH - 8;
            int x = targetX + (int) Math.round((CARD_WIDTH + 16) * (1.0 - eased));
            int y = graphics.guiHeight() - 8 - CARD_HEIGHT - index * (CARD_HEIGHT + GAP);
            card.bounds = new Bounds(x, y, CARD_WIDTH, CARD_HEIGHT);
            boolean hovered = card.bounds.contains(mouseX, mouseY);
            if (hovered && card.expiresAt > 0) card.expiresAt = Math.max(card.expiresAt, now + 1000L);
            drawCard(graphics, card, hovered);
            index++;
        }
    }

    public synchronized boolean handleMouseClick(int button) {
        if (!config.notificationsEnabled) return false;
        int x = (int) minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
        int y = (int) minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
        for (Card card : cards) {
            if (!card.bounds.contains(x, y)) continue;
            if (button == 0) {
                if (card.image != null) {
                    gallery.find(card.id).ifPresent(HyperShotClient::openViewer);
                } else if (card.status == Status.FAILED) {
                    platform.open(HyperShotClient.paths().logs());
                } else {
                    HyperShotClient.openQuickCapture();
                }
                return true;
            }
            if (button == 1 && card.image != null) {
                platform.reveal(card.image);
                return true;
            }
        }
        return false;
    }

    private void drawCard(GuiGraphicsExtractor graphics, Card card, boolean hovered) {
        Bounds b = card.bounds;
        int background = hovered ? 0xEE202329 : 0xE8191C21;
        int border = switch (card.status) {
            case ACTIVE -> 0xFF56A8FF;
            case COMPLETE -> 0xFF69D38B;
            case FAILED -> 0xFFFF6B6B;
            case CANCELLED -> 0xFFFFC857;
        };
        graphics.fill(b.x + 3, b.y + 3, b.x + b.width + 3, b.y + b.height + 3, 0x66000000);
        graphics.fill(b.x, b.y, b.x + b.width, b.y + b.height, background);
        graphics.outline(b.x, b.y, b.width, b.height, 0xFF424956);
        graphics.fill(b.x, b.y, b.x + 3, b.y + b.height, border);

        int textX = b.x + 10;
        int previewWidth = 92;
        if (card.thumbnail != null) {
            Identifier texture = textures.get(card.thumbnail);
            if (texture != null && card.request != null) {
                var thumb = ThumbnailGenerator.fit(card.request.resolution().width(), card.request.resolution().height(), previewWidth, 56);
                int previewY = b.y + (b.height - thumb.height()) / 2;
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture, textX, previewY, 0, 0,
                        thumb.width(), thumb.height(), thumb.width(), thumb.height());
                textX += previewWidth + 8;
            }
        }

        String title = card.request == null ? "HyperShot" : card.request.presetName();
        graphics.text(minecraft.font, title, textX, b.y + 9, 0xFFF2F4F8, true);
        String detail = switch (card.status) {
            case ACTIVE -> phaseText(card.progress);
            case COMPLETE -> card.request.resolution().width() + "×" + card.request.resolution().height() + "  " + humanBytes(card.fileSize);
            case FAILED -> truncate(card.message, 26);
            case CANCELLED -> truncate(card.message, 26);
        };
        graphics.text(minecraft.font, detail, textX, b.y + 27, 0xFFB8BEC8, false);

        if (card.status == Status.ACTIVE && card.progress != null) {
            int barX = textX;
            int barY = b.y + 49;
            int barWidth = b.x + b.width - 8 - barX;
            graphics.fill(barX, barY, barX + barWidth, barY + 5, 0xFF3A3F48);
            int fill = (int) Math.round(barWidth * card.progress.pixelsFraction());
            graphics.fill(barX, barY, barX + fill, barY + 5, 0xFF56A8FF);
            String progress = card.progress.tilesCompleted() + "/" + card.progress.totalTiles() + " tiles";
            graphics.text(minecraft.font, progress, barX, barY + 9, 0xFF929AA7, false);
        } else {
            graphics.text(minecraft.font, card.status == Status.COMPLETE ? "Click to preview • Right-click to reveal" : "Click for details",
                    textX, b.y + 51, 0xFF929AA7, false);
        }
    }

    private synchronized void remove(Card card) {
        cards.remove(card);
        byId.remove(card.id);
    }

    private static String phaseText(CaptureProgressSnapshot progress) {
        if (progress == null) return "Preparing…";
        return switch (progress.phase()) {
            case CAPTURING_TILES -> "Capturing tiles • " + percent(progress.pixelsFraction());
            case ENCODING -> "Encoding " + (progress == null ? "image" : "image") + "…";
            case GENERATING_THUMBNAIL -> "Generating preview…";
            case WRITING_METADATA -> "Writing metadata…";
            default -> progress.phase().name().replace('_', ' ').toLowerCase();
        };
    }

    private static String percent(double value) { return Math.round(value * 100.0) + "%"; }
    private static String truncate(String value, int length) {
        if (value == null) return "";
        return value.length() <= length ? value : value.substring(0, Math.max(0, length - 1)) + "…";
    }
    private static String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes;
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++; }
        return String.format(java.util.Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    private enum Status { ACTIVE, COMPLETE, FAILED, CANCELLED }
    private static final class Card {
        final String id;
        final CaptureRequest request;
        final long createdAt;
        volatile CaptureProgressSnapshot progress;
        volatile Status status = Status.ACTIVE;
        volatile String message = "";
        volatile Path image;
        volatile Path metadata;
        volatile Path thumbnail;
        volatile long fileSize;
        volatile long completedAt;
        volatile long expiresAt;
        volatile Bounds bounds = Bounds.EMPTY;
        Card(String id, CaptureRequest request, long createdAt) { this.id = id; this.request = request; this.createdAt = createdAt; }
    }
    private record Bounds(int x, int y, int width, int height) {
        static final Bounds EMPTY = new Bounds(0, 0, 0, 0);
        boolean contains(int px, int py) { return px >= x && py >= y && px < x + width && py < y + height; }
    }
}
