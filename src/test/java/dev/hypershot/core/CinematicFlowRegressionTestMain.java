package dev.hypershot.core;

import java.lang.reflect.Method;

public final class CinematicFlowRegressionTestMain {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        fullPreparedShotFlow();
        noChunkWaitSkipsDirectlyToSettle();
        cancelJumpsToRestoration();
        System.out.println("HyperShot cinematic flow tests: PASS (" + assertions + " assertions)");
    }

    private static void fullPreparedShotFlow() throws Exception {
        Object machine = newMachine();
        invoke(machine, "start");
        eq("SNAPSHOTTING", state(machine), "cinematic begins by snapshotting scene");
        invoke(machine, "snapshotReady");
        eq("APPLYING_SCENE", state(machine), "snapshot leads to scene application");
        invoke(machine, "sceneApplied", boolean.class, true);
        eq("WAITING_FOR_CHUNKS", state(machine), "chunk-ready option waits before freezing/capture");
        invoke(machine, "chunksReady");
        eq("SETTLING", state(machine), "loaded chunks lead to shader settle");
        invoke(machine, "settleReady");
        eq("READY_TO_CAPTURE", state(machine), "settled scene is ready to capture");
        invoke(machine, "captureStarted");
        eq("CAPTURING", state(machine), "capture start is explicit");
        invoke(machine, "finishCapture");
        eq("RESTORING", state(machine), "successful capture restores scene");
        invoke(machine, "restored");
        eq("COMPLETE", state(machine), "restoration completes the cinematic session");
    }

    private static void noChunkWaitSkipsDirectlyToSettle() throws Exception {
        Object machine = newMachine();
        invoke(machine, "start");
        invoke(machine, "snapshotReady");
        invoke(machine, "sceneApplied", boolean.class, false);
        eq("SETTLING", state(machine), "disabled chunk wait proceeds directly to settle");
    }

    private static void cancelJumpsToRestoration() throws Exception {
        Object machine = newMachine();
        invoke(machine, "start");
        invoke(machine, "snapshotReady");
        invoke(machine, "sceneApplied", boolean.class, true);
        invoke(machine, "cancel");
        eq("RESTORING", state(machine), "cancel from chunk wait restores scene");
        invoke(machine, "cancel");
        eq("RESTORING", state(machine), "repeat cancel remains idempotent");
    }

    private static Object newMachine() throws Exception {
        try {
            Class<?> type = Class.forName("dev.hypershot.core.camera.CinematicFlowMachine");
            return type.getConstructor().newInstance();
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("CinematicFlowMachine is missing; Cinematic mode has no tested orchestration order", missing);
        }
    }

    private static String state(Object machine) throws Exception {
        Object value = machine.getClass().getMethod("state").invoke(machine);
        return ((Enum<?>) value).name();
    }

    private static void invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getMethod(name);
        method.invoke(target);
    }

    private static void invoke(Object target, String name, Class<?> argType, Object value) throws Exception {
        Method method = target.getClass().getMethod(name, argType);
        method.invoke(target, value);
    }

    private static void eq(Object expected, Object actual, String name) {
        assertions++;
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(name + ": expected=" + expected + " actual=" + actual);
        }
    }
}
