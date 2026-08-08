package dev.hypershot.gallery;

import dev.hypershot.capture.CaptureListener;

import java.nio.file.Path;
import java.util.Objects;

/** Runs after the normal notification/gallery listener and adds higher-level Burst/Time metadata. */
public final class CaptureGroupAnnotator implements CaptureListener {
    private final CaptureGroupContext context;
    private final GalleryIndex gallery;

    public CaptureGroupAnnotator(CaptureGroupContext context, GalleryIndex gallery) {
        this.context = Objects.requireNonNull(context);
        this.gallery = Objects.requireNonNull(gallery);
    }

    @Override
    public void onCompleted(String captureId, Path image, Path metadata, Path thumbnail, long fileSize) {
        CaptureGroupContext.GroupInfo info = context.remove(captureId);
        if (info == null) return;
        gallery.find(captureId).ifPresent(record -> {
            apply(record, info, true);
            gallery.add(record);
        });
    }

    @Override
    public void onCancelled(String captureId, String reason) {
        CaptureGroupContext.GroupInfo info = context.remove(captureId);
        if (info != null) markIncomplete(info.groupId());
    }

    @Override
    public void onFailed(String captureId, String message, Throwable error) {
        CaptureGroupContext.GroupInfo info = context.remove(captureId);
        if (info != null) markIncomplete(info.groupId());
    }

    public void markIncomplete(String groupId) {
        if (groupId == null) return;
        for (CaptureRecord record : gallery.all()) {
            if (!groupId.equals(record.groupId)) continue;
            record.groupComplete = false;
            gallery.add(record);
        }
    }

    private static void apply(CaptureRecord record, CaptureGroupContext.GroupInfo info, boolean completeSoFar) {
        record.groupId = info.groupId();
        record.groupType = info.groupType();
        record.groupIndex = info.groupIndex();
        record.groupCount = info.groupCount();
        record.groupLabel = info.groupLabel();
        record.groupComplete = completeSoFar;
    }
}
