package dev.hypershot.core;

public final class CaptureCancelledException extends RuntimeException {
    public CaptureCancelledException(String reason) { super(reason == null || reason.isBlank() ? "Capture cancelled" : reason); }
}
