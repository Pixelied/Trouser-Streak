package dev.hypershot.core.camera;

public final class OverlayFadePolicy {
    public static final long IDLE_DELAY_NANOS = 1_500_000_000L;
    public static final float IDLE_ALPHA = 0.35f;

    private OverlayFadePolicy() {}

    public static float alpha(long nowNanos, long lastActivityNanos, boolean enabled, boolean busy) {
        if (!enabled || busy) return 1.0f;
        long elapsed = nowNanos <= lastActivityNanos ? 0L : nowNanos - lastActivityNanos;
        return elapsed >= IDLE_DELAY_NANOS ? IDLE_ALPHA : 1.0f;
    }
}
