package dev.hypershot.capture;

import dev.hypershot.core.CaptureProgressSnapshot;

import java.nio.file.Path;

public interface CaptureListener {
    default void onStarted(String captureId, CaptureRequest request) {}
    default void onProgress(String captureId, CaptureProgressSnapshot progress) {}
    default void onCompleted(String captureId, Path image, Path metadata, Path thumbnail, long fileSize) {}
    default void onCancelled(String captureId, String reason) {}
    default void onFailed(String captureId, String message, Throwable error) {}
}
