package dev.hypershot.core.camera;

public enum CinematicTimePreset {
    CURRENT(null),
    SUNRISE(TimeOfDayState.SUNRISE.dayTime()),
    MORNING(TimeOfDayState.MORNING.dayTime()),
    NOON(TimeOfDayState.NOON.dayTime()),
    GOLDEN_HOUR(TimeOfDayState.GOLDEN_HOUR.dayTime()),
    SUNSET(TimeOfDayState.SUNSET.dayTime()),
    BLUE_HOUR(TimeOfDayState.BLUE_HOUR.dayTime()),
    NIGHT(TimeOfDayState.NIGHT.dayTime());

    private final Long dayTime;

    CinematicTimePreset(Long dayTime) {
        this.dayTime = dayTime;
    }

    public Long dayTime() {
        return dayTime;
    }
}
