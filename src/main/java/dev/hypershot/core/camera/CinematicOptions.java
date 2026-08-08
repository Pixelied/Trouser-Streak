package dev.hypershot.core.camera;

import java.util.Objects;

public record CinematicOptions(boolean worldFreeze, CinematicTimePreset timePreset,
                               CinematicWeatherPreset weatherPreset, boolean waitForChunks,
                               int chunkTimeoutMs, boolean cameraLock, boolean fovLock, boolean cleanFrame) {
    public CinematicOptions {
        Objects.requireNonNull(timePreset, "timePreset");
        Objects.requireNonNull(weatherPreset, "weatherPreset");
        if (chunkTimeoutMs < 1 || chunkTimeoutMs > 120_000) {
            throw new IllegalArgumentException("Chunk timeout must be 1-120000 ms");
        }
    }

    public CinematicOptions gatedBy(CinematicCapabilities capabilities) {
        Objects.requireNonNull(capabilities);
        return new CinematicOptions(worldFreeze && capabilities.worldFreeze(),
                capabilities.authoritativeTime() ? timePreset : CinematicTimePreset.CURRENT,
                capabilities.authoritativeWeather() ? weatherPreset : CinematicWeatherPreset.CURRENT,
                waitForChunks && capabilities.chunkReadiness(), chunkTimeoutMs,
                cameraLock && capabilities.cameraLock(), fovLock && capabilities.fovLock(), cleanFrame);
    }
}
