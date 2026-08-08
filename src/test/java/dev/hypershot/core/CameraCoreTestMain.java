package dev.hypershot.core;

import dev.hypershot.core.camera.ShaderSettleProfile;
import dev.hypershot.core.camera.ShotPreparationMachine;
import dev.hypershot.core.camera.ShotReadinessState;

public final class CameraCoreTestMain {
    private static int assertions;

    public static void main(String[] args) {
        timerThenSettleThenReady();
        movementRestartsShaderSettle();
        cancelIsTerminal();
        settleProfilesAreBounded();
        System.out.println("HyperShot camera core tests: PASS (" + assertions + " assertions)");
    }

    private static void timerThenSettleThenReady() {
        ShotPreparationMachine machine = new ShotPreparationMachine();
        machine.start(0L, 3_000_000_000L, 1_000_000_000L);
        eq(ShotReadinessState.WAITING_FOR_TIMER, machine.readiness(0L).state(), "timer starts first");
        machine.tick(2_999_999_999L);
        eq(ShotReadinessState.WAITING_FOR_TIMER, machine.readiness(2_999_999_999L).state(), "timer remains pending");
        machine.tick(3_000_000_000L);
        eq(ShotReadinessState.SETTLING_SHADERS, machine.readiness(3_000_000_000L).state(), "settle follows timer");
        machine.tick(4_000_000_000L);
        eq(ShotReadinessState.READY, machine.readiness(4_000_000_000L).state(), "ready after settle");
    }

    private static void movementRestartsShaderSettle() {
        ShotPreparationMachine machine = new ShotPreparationMachine();
        machine.start(0L, 0L, 1_000_000_000L);
        eq(ShotReadinessState.SETTLING_SHADERS, machine.readiness(0L).state(), "settle starts without timer");
        machine.noteSceneChanged(500_000_000L);
        machine.tick(1_000_000_000L);
        eq(ShotReadinessState.SETTLING_SHADERS, machine.readiness(1_000_000_000L).state(), "camera movement restarts settle");
        machine.tick(1_500_000_000L);
        eq(ShotReadinessState.READY, machine.readiness(1_500_000_000L).state(), "ready after restarted settle");
    }

    private static void cancelIsTerminal() {
        ShotPreparationMachine machine = new ShotPreparationMachine();
        machine.start(0L, 3_000_000_000L, 1_000_000_000L);
        machine.cancel();
        machine.tick(10_000_000_000L);
        eq(ShotReadinessState.CANCELLED, machine.readiness(10_000_000_000L).state(), "cancel terminal state");
    }

    private static void settleProfilesAreBounded() {
        eq(250L, ShaderSettleProfile.QUICK.resolveMillis(999), "quick settle");
        eq(1_000L, ShaderSettleProfile.STANDARD.resolveMillis(999), "standard settle");
        eq(2_500L, ShaderSettleProfile.DEEP.resolveMillis(999), "deep settle");
        eq(0L, ShaderSettleProfile.OFF.resolveMillis(999), "off settle");
        eq(0L, ShaderSettleProfile.CUSTOM.resolveMillis(-50), "custom settle lower clamp");
        eq(10_000L, ShaderSettleProfile.CUSTOM.resolveMillis(99_999), "custom settle upper clamp");
    }

    private static void eq(Object expected, Object actual, String name) {
        assertions++;
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(name + ": expected=" + expected + " actual=" + actual);
        }
    }
}
