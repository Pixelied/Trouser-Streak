package dev.hypershot.core;

import java.util.ArrayDeque;
import java.util.Deque;

public final class StateRestorationScope implements AutoCloseable {
    private final Deque<Runnable> restorers = new ArrayDeque<>();
    private boolean closed;

    public void change(Runnable apply, Runnable restore) {
        ensureOpen();
        apply.run();
        restorers.push(restore);
    }

    public void onRestore(Runnable restore) {
        ensureOpen();
        restorers.push(restore);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        while (!restorers.isEmpty()) {
            try { restorers.pop().run(); }
            catch (RuntimeException ex) { if (failure == null) failure = ex; else failure.addSuppressed(ex); }
        }
        if (failure != null) throw failure;
    }

    private void ensureOpen() { if (closed) throw new IllegalStateException("Scope already closed"); }
}
