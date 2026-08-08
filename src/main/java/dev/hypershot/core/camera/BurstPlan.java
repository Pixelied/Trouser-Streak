package dev.hypershot.core.camera;

public record BurstPlan(int frameCount, long intervalMillis) {
    public BurstPlan {
        if (frameCount < 1 || frameCount > 100) throw new IllegalArgumentException("Burst frame count must be 1-100");
        if (intervalMillis < 0 || intervalMillis > 60_000) throw new IllegalArgumentException("Burst interval must be 0-60000 ms");
    }
}
