package dev.hypershot.core.camera;

public final class ShotPreparationMachine {
    private ShotReadinessState state = ShotReadinessState.READY;
    private long timerDeadlineNanos;
    private long settleDurationNanos;
    private long settleDeadlineNanos;

    public void start(long nowNanos, long timerNanos, long settleNanos) {
        if (timerNanos < 0 || settleNanos < 0) throw new IllegalArgumentException("Preparation durations must be non-negative");
        settleDurationNanos = settleNanos;
        timerDeadlineNanos = addSaturated(nowNanos, timerNanos);
        settleDeadlineNanos = addSaturated(nowNanos, settleNanos);
        if (timerNanos > 0) {
            state = ShotReadinessState.WAITING_FOR_TIMER;
        } else if (settleNanos > 0) {
            state = ShotReadinessState.SETTLING_SHADERS;
        } else {
            state = ShotReadinessState.READY;
        }
    }

    public void tick(long nowNanos) {
        if (state == ShotReadinessState.WAITING_FOR_TIMER && nowNanos >= timerDeadlineNanos) {
            if (settleDurationNanos > 0) {
                state = ShotReadinessState.SETTLING_SHADERS;
                settleDeadlineNanos = addSaturated(nowNanos, settleDurationNanos);
            } else {
                state = ShotReadinessState.READY;
            }
        }
        if (state == ShotReadinessState.SETTLING_SHADERS && nowNanos >= settleDeadlineNanos) {
            state = ShotReadinessState.READY;
        }
    }

    public void noteSceneChanged(long nowNanos) {
        if (state == ShotReadinessState.SETTLING_SHADERS && settleDurationNanos > 0) {
            settleDeadlineNanos = addSaturated(nowNanos, settleDurationNanos);
        }
    }

    public void cancel() {
        state = ShotReadinessState.CANCELLED;
    }

    public ShotReadiness readiness(long nowNanos) {
        return switch (state) {
            case WAITING_FOR_TIMER -> new ShotReadiness(state, "Waiting for timer", remaining(timerDeadlineNanos, nowNanos));
            case SETTLING_SHADERS -> new ShotReadiness(state, "Settling shaders", remaining(settleDeadlineNanos, nowNanos));
            case CANCELLED -> new ShotReadiness(state, "Shot cancelled", 0L);
            default -> new ShotReadiness(state, "", 0L);
        };
    }

    private static long remaining(long deadline, long now) {
        return deadline <= now ? 0L : deadline - now;
    }

    private static long addSaturated(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
