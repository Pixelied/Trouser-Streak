package dev.hypershot.capture;

import dev.hypershot.core.CaptureProgressSnapshot;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class CaptureListenerHub implements CaptureListener {
    private final Logger logger;
    private final CopyOnWriteArrayList<CaptureListener> listeners = new CopyOnWriteArrayList<>();

    public CaptureListenerHub(Logger logger) {
        this.logger = Objects.requireNonNull(logger);
    }

    public void add(CaptureListener listener) {
        listeners.addIfAbsent(Objects.requireNonNull(listener));
    }

    public void remove(CaptureListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void onStarted(String captureId, CaptureRequest request) {
        dispatch(listener -> listener.onStarted(captureId, request));
    }

    @Override
    public void onProgress(String captureId, CaptureProgressSnapshot progress) {
        dispatch(listener -> listener.onProgress(captureId, progress));
    }

    @Override
    public void onCompleted(String captureId, Path image, Path metadata, Path thumbnail, long fileSize) {
        dispatch(listener -> listener.onCompleted(captureId, image, metadata, thumbnail, fileSize));
    }

    @Override
    public void onCancelled(String captureId, String reason) {
        dispatch(listener -> listener.onCancelled(captureId, reason));
    }

    @Override
    public void onFailed(String captureId, String message, Throwable error) {
        dispatch(listener -> listener.onFailed(captureId, message, error));
    }

    private void dispatch(Consumer<CaptureListener> callback) {
        for (CaptureListener listener : listeners) {
            try {
                callback.accept(listener);
            } catch (Throwable error) {
                logger.error("HyperShot capture listener {} failed", listener.getClass().getName(), error);
            }
        }
    }
}
