package dev.hypershot.core.camera;

/** Pure orchestration lifecycle for one Cinematic photo. Minecraft side effects live outside this class. */
public final class CinematicFlowMachine {
    public enum State {
        IDLE,
        SNAPSHOTTING,
        APPLYING_SCENE,
        WAITING_FOR_CHUNKS,
        SETTLING,
        READY_TO_CAPTURE,
        CAPTURING,
        RESTORING,
        COMPLETE
    }

    private State state = State.IDLE;

    public State state() {
        return state;
    }

    public void start() {
        if (state != State.IDLE && state != State.COMPLETE) throw invalid("start");
        state = State.SNAPSHOTTING;
    }

    public void snapshotReady() {
        require(State.SNAPSHOTTING, "snapshotReady");
        state = State.APPLYING_SCENE;
    }

    public void sceneApplied(boolean waitForChunks) {
        require(State.APPLYING_SCENE, "sceneApplied");
        state = waitForChunks ? State.WAITING_FOR_CHUNKS : State.SETTLING;
    }

    public void chunksReady() {
        require(State.WAITING_FOR_CHUNKS, "chunksReady");
        state = State.SETTLING;
    }

    public void settleReady() {
        require(State.SETTLING, "settleReady");
        state = State.READY_TO_CAPTURE;
    }

    public void captureStarted() {
        require(State.READY_TO_CAPTURE, "captureStarted");
        state = State.CAPTURING;
    }

    public void finishCapture() {
        require(State.CAPTURING, "finishCapture");
        state = State.RESTORING;
    }

    public void cancel() {
        if (state == State.RESTORING || state == State.COMPLETE) return;
        if (state == State.IDLE) {
            state = State.COMPLETE;
            return;
        }
        state = State.RESTORING;
    }

    public void restored() {
        require(State.RESTORING, "restored");
        state = State.COMPLETE;
    }

    private void require(State expected, String operation) {
        if (state != expected) throw invalid(operation);
    }

    private IllegalStateException invalid(String operation) {
        return new IllegalStateException(operation + " is invalid while Cinematic flow is " + state);
    }
}
