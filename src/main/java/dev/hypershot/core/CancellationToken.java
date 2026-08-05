package dev.hypershot.core;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicReference<String> reason = new AtomicReference<>("");

    public boolean cancel(String reason) {
        this.reason.compareAndSet("", reason == null ? "Capture cancelled" : reason);
        return cancelled.compareAndSet(false, true);
    }

    public boolean isCancelled() { return cancelled.get(); }
    public String reason() { return reason.get(); }
    public void throwIfCancelled() { if (isCancelled()) throw new CaptureCancelledException(reason()); }
}
