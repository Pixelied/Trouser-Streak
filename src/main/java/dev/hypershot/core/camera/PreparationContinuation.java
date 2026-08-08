package dev.hypershot.core.camera;

import java.util.Objects;

/** Pure flow rule for what happens after a preparation/settle window becomes ready. */
public final class PreparationContinuation {
    public enum Action {
        APPLY_TIME,
        CAPTURE
    }

    private PreparationContinuation() {}

    public static Action next(CameraMode mode, boolean postSceneSettle) {
        Objects.requireNonNull(mode);
        if (postSceneSettle) return Action.CAPTURE;
        return mode == CameraMode.TIME ? Action.APPLY_TIME : Action.CAPTURE;
    }
}
