package dev.hypershot.core.camera;

public record CinematicCapabilities(boolean worldFreeze, boolean authoritativeTime, boolean authoritativeWeather,
                                    boolean cameraLock, boolean fovLock, boolean chunkReadiness) {
    public static CinematicCapabilities multiplayer() {
        return new CinematicCapabilities(false, false, false, true, true, true);
    }

    public static CinematicCapabilities singleplayer(boolean vanillaTickFreezeAvailable) {
        return new CinematicCapabilities(vanillaTickFreezeAvailable, true, true, true, true, true);
    }
}
