package dev.hypershot.core;

import dev.hypershot.core.camera.OverlayFadePolicy;

public final class OverlayFadeTestMain {
    private static int assertions;

    public static void main(String[] args) {
        eq(1.0f, OverlayFadePolicy.alpha(3_000_000_000L, 0L, false, false), "disabled fade stays visible");
        eq(1.0f, OverlayFadePolicy.alpha(1_000_000_000L, 0L, true, false), "recent activity stays visible");
        eq(0.35f, OverlayFadePolicy.alpha(2_000_000_000L, 0L, true, false), "idle chrome fades");
        eq(1.0f, OverlayFadePolicy.alpha(2_000_000_000L, 0L, true, true), "busy camera stays visible");
        System.out.println("HyperShot overlay fade tests: PASS (" + assertions + " assertions)");
    }

    private static void eq(Object expected, Object actual, String name) {
        assertions++;
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError(name + ": expected=" + expected + " actual=" + actual);
    }
}
