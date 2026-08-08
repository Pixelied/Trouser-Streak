package dev.hypershot.core;

import dev.hypershot.core.camera.CameraMode;

import java.lang.reflect.Method;

/** Regression for Time mode: once a lighting change has settled, the frame must capture instead of reapplying time forever. */
public final class TimeFlowRegressionTestMain {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        initialTimePreparationAppliesLighting();
        postLightingSettleCapturesFrame();
        System.out.println("HyperShot Time flow regression tests: PASS (" + assertions + " assertions)");
    }

    private static void initialTimePreparationAppliesLighting() throws Exception {
        eq("APPLY_TIME", continuation(CameraMode.TIME, false), "initial Time preparation applies lighting");
    }

    private static void postLightingSettleCapturesFrame() throws Exception {
        eq("CAPTURE", continuation(CameraMode.TIME, true), "post-lighting settle captures instead of applying time again");
    }

    private static String continuation(CameraMode mode, boolean postSceneSettle) throws Exception {
        final Class<?> policy;
        try {
            policy = Class.forName("dev.hypershot.core.camera.PreparationContinuation");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("PreparationContinuation policy is missing; Time mode can loop after shader settle", missing);
        }
        Method next = policy.getMethod("next", CameraMode.class, boolean.class);
        Object action = next.invoke(null, mode, postSceneSettle);
        if (!(action instanceof Enum<?> value)) {
            throw new AssertionError("PreparationContinuation.next must return an enum action");
        }
        return value.name();
    }

    private static void eq(Object expected, Object actual, String name) {
        assertions++;
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(name + ": expected=" + expected + " actual=" + actual);
        }
    }
}
