package dev.hypershot.core.camera;

public enum ShaderSettleProfile {
    OFF(0),
    QUICK(250),
    STANDARD(1_000),
    DEEP(2_500),
    CUSTOM(-1);

    private final long defaultMillis;

    ShaderSettleProfile(long defaultMillis) {
        this.defaultMillis = defaultMillis;
    }

    public long defaultMillis() {
        return defaultMillis < 0 ? 1_000 : defaultMillis;
    }

    public long resolveMillis(long customMillis) {
        if (this != CUSTOM) return Math.max(0L, defaultMillis);
        return Math.max(0L, Math.min(10_000L, customMillis));
    }
}
