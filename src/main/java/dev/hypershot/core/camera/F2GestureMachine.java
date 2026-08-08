package dev.hypershot.core.camera;

public final class F2GestureMachine {
    public enum Action { NONE, TAP, HOLD }

    private boolean down;
    private boolean holdFired;
    private long pressedAtNanos;

    public Action update(boolean isDown, long nowNanos, long thresholdNanos) {
        if (thresholdNanos < 0) throw new IllegalArgumentException("Hold threshold must be non-negative");
        if (isDown) {
            if (!down) {
                down = true;
                holdFired = false;
                pressedAtNanos = nowNanos;
                return thresholdNanos == 0 ? fireHold() : Action.NONE;
            }
            if (!holdFired && elapsed(nowNanos, pressedAtNanos) >= thresholdNanos) {
                return fireHold();
            }
            return Action.NONE;
        }
        if (!down) return Action.NONE;
        down = false;
        if (holdFired) {
            holdFired = false;
            return Action.NONE;
        }
        return Action.TAP;
    }

    public void reset() {
        down = false;
        holdFired = false;
        pressedAtNanos = 0L;
    }

    private Action fireHold() {
        holdFired = true;
        return Action.HOLD;
    }

    private static long elapsed(long nowNanos, long startNanos) {
        if (nowNanos <= startNanos) return 0L;
        return nowNanos - startNanos;
    }
}
