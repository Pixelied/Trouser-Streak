package dev.hypershot.core.camera;

public enum ShotReadinessState {
    READY,
    WAITING_FOR_TIMER,
    SETTLING_SHADERS,
    WAITING_FOR_INTERVAL,
    WAITING_FOR_CHUNKS,
    PREPARING_SCENE,
    HIGH_LOAD,
    BLOCKED,
    CAPTURING,
    FINALIZING,
    CANCELLED,
    FAILED
}
