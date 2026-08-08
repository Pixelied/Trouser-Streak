package dev.hypershot.core.camera;

/** Photographic labels mapped to Minecraft's 24,000-tick day. */
public enum TimeOfDayState {
    SUNRISE("Sunrise", 23_000L),
    MORNING("Morning", 1_000L),
    NOON("Noon", 6_000L),
    GOLDEN_HOUR("Golden Hour", 10_500L),
    SUNSET("Sunset", 12_000L),
    BLUE_HOUR("Blue Hour", 13_000L),
    NIGHT("Night", 15_000L);

    private final String label;
    private final long dayTime;

    TimeOfDayState(String label, long dayTime) {
        this.label = label;
        this.dayTime = dayTime;
    }

    public String label() { return label; }
    public long dayTime() { return dayTime; }
}
