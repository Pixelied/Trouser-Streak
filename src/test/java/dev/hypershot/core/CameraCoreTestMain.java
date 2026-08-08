package dev.hypershot.core;

import dev.hypershot.core.camera.CameraSceneSignature;
import dev.hypershot.core.camera.F2GestureMachine;
import dev.hypershot.core.camera.GuideGeometry;
import dev.hypershot.core.camera.GuideType;
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
        guideGeometry();
        f2Gesture();
        sceneSignature();
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

    private static void guideGeometry() {
        var thirds = GuideGeometry.lines(GuideType.RULE_OF_THIRDS, 900, 600);
        eq(4, thirds.size(), "thirds line count");
        eq(new GuideGeometry.Line(300, 0, 300, 600), thirds.get(0), "thirds first vertical");
        eq(new GuideGeometry.Line(600, 0, 600, 600), thirds.get(1), "thirds second vertical");
        var cross = GuideGeometry.lines(GuideType.CENTER_CROSS, 900, 600);
        eq(2, cross.size(), "center cross line count");
        eq(0, GuideGeometry.lines(GuideType.OFF, 900, 600).size(), "off has no lines");
        for (GuideType type : new GuideType[]{GuideType.GOLDEN_RATIO, GuideType.HORIZON, GuideType.DIAGONAL, GuideType.SAFE_FRAME}) {
            for (GuideGeometry.Line line : GuideGeometry.lines(type, 900, 600)) {
                check(line.x1() >= 0 && line.x1() <= 900 && line.x2() >= 0 && line.x2() <= 900, type + " x bounds");
                check(line.y1() >= 0 && line.y1() <= 600 && line.y2() >= 0 && line.y2() <= 600, type + " y bounds");
            }
        }
    }

    private static void f2Gesture() {
        F2GestureMachine tap = new F2GestureMachine();
        eq(F2GestureMachine.Action.NONE, tap.update(true, 0L, 350_000_000L), "press starts pending");
        eq(F2GestureMachine.Action.TAP, tap.update(false, 100_000_000L, 350_000_000L), "short release is tap");
        eq(F2GestureMachine.Action.NONE, tap.update(false, 120_000_000L, 350_000_000L), "tap fires once");
        F2GestureMachine hold = new F2GestureMachine();
        eq(F2GestureMachine.Action.NONE, hold.update(true, 0L, 350_000_000L), "hold press starts pending");
        eq(F2GestureMachine.Action.HOLD, hold.update(true, 350_000_000L, 350_000_000L), "threshold emits hold");
        eq(F2GestureMachine.Action.NONE, hold.update(true, 500_000_000L, 350_000_000L), "hold fires once");
        eq(F2GestureMachine.Action.NONE, hold.update(false, 600_000_000L, 350_000_000L), "release after hold is not tap");
    }

    private static void sceneSignature() {
        CameraSceneSignature base = new CameraSceneSignature(10.0, 64.0, -3.0, 90.0f, 12.0f, 70.0f);
        check(!base.meaningfullyDiffers(new CameraSceneSignature(10.00001, 64.0, -3.0, 90.005f, 12.005f, 70.005f)), "tiny camera jitter ignored");
        check(base.meaningfullyDiffers(new CameraSceneSignature(10.01, 64.0, -3.0, 90.0f, 12.0f, 70.0f)), "position movement detected");
        check(base.meaningfullyDiffers(new CameraSceneSignature(10.0, 64.0, -3.0, 90.1f, 12.0f, 70.0f)), "yaw movement detected");
        check(base.meaningfullyDiffers(new CameraSceneSignature(10.0, 64.0, -3.0, 90.0f, 12.1f, 70.0f)), "pitch movement detected");
        check(base.meaningfullyDiffers(new CameraSceneSignature(10.0, 64.0, -3.0, 90.0f, 12.0f, 71.0f)), "fov movement detected");
    }

    private static void check(boolean value, String name) {
        assertions++;
        if (!value) throw new AssertionError(name);
    }

    private static void eq(Object expected, Object actual, String name) {
        assertions++;
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(name + ": expected=" + expected + " actual=" + actual);
        }
    }
}
