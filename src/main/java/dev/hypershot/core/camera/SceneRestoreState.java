package dev.hypershot.core.camera;

/** Tiny synchronized lifecycle guard so scene restoration remains idempotent across success/cancel/error races. */
public final class SceneRestoreState {
    public enum Phase { NOT_CAPTURED, CAPTURED, RESTORING, RESTORED }

    private Phase phase = Phase.NOT_CAPTURED;

    /** Begin a fresh scene-control session after construction or a completed restoration. */
    public synchronized boolean beginSession() {
        if (phase == Phase.CAPTURED || phase == Phase.RESTORING) return false;
        phase = Phase.NOT_CAPTURED;
        return true;
    }

    public synchronized boolean markCaptured() {
        if (phase != Phase.NOT_CAPTURED) return false;
        phase = Phase.CAPTURED;
        return true;
    }

    public synchronized boolean beginRestore() {
        if (phase != Phase.NOT_CAPTURED && phase != Phase.CAPTURED) return false;
        phase = Phase.RESTORING;
        return true;
    }

    public synchronized void markRestored() {
        if (phase == Phase.RESTORING || phase == Phase.CAPTURED) phase = Phase.RESTORED;
    }

    public synchronized Phase phase() {
        return phase;
    }
}
