package dev.hypershot.core.camera;

import java.util.Objects;

public record ShotReadiness(ShotReadinessState state, String reason, long remainingNanos) {
    public ShotReadiness {
        Objects.requireNonNull(state, "state");
        reason = reason == null ? "" : reason;
        if (remainingNanos < 0) remainingNanos = 0;
    }
}
