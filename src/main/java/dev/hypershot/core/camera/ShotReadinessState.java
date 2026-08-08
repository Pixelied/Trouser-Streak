package dev.hypershot.core.camera;

public enum ShotReadinessState {
    READY,
    WAITING_FOR_TIMER,
    SETTLING_SHADERS,
    HIGH_LOAD,
    BLOCKED,
    CAPTURING,
    FINALIZING,
    CANCELLED,
    FAILED
}
