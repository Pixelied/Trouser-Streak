package dev.hypershot.core;

import dev.hypershot.core.camera.BurstPlan;
import dev.hypershot.core.camera.SequenceEstimate;
import dev.hypershot.core.camera.SequenceEstimateCalculator;
import dev.hypershot.core.camera.SequencePreflight;
import dev.hypershot.core.camera.SequencePreflightCalculator;
import dev.hypershot.core.camera.TimeBracketPlan;
import dev.hypershot.core.camera.TimeOfDayState;

import java.util.List;

public final class SequenceCoreTestMain {
    private static int assertions;

    public static void main(String[] args) {
        burstPlan();
        sequenceEstimate();
        sequencePreflight();
        timeBracket();
        System.out.println("HyperShot sequence core tests: PASS (" + assertions + " assertions)");
    }

    private static void burstPlan() {
        BurstPlan plan = new BurstPlan(5, 250);
        eq(5, plan.frameCount(), "burst count");
        eq(250L, plan.intervalMillis(), "burst interval");
        expectThrows(() -> new BurstPlan(0, 250), "zero burst rejected");
        expectThrows(() -> new BurstPlan(101, 250), "oversized burst rejected");
    }

    private static void sequenceEstimate() {
        CaptureEstimate per = CaptureEstimator.estimate(new CaptureSpec(30720, 17280, 2048, 32, 1, OutputFormat.PNG));
        SequenceEstimate estimate = SequenceEstimateCalculator.multiply(per, 10);
        eq(5_308_416_000L, estimate.totalOutputPixels(), "10x32K pixels");
        eq(Math.multiplyExact(per.estimatedFinalBytes(), 10L), estimate.totalFinalBytes(), "final bytes multiplied");
        eq(per.temporaryBytes(), estimate.worstTemporaryBytes(), "temporary storage stays sequential");
        eq(per.peakHeapBytes(), estimate.peakHeapBytes(), "peak heap remains per frame");
    }

    private static void sequencePreflight() {
        CaptureEstimate estimate = new CaptureEstimate(100, 110, 100, 1_000, 500);
        CapturePreflight safeFrame = new CapturePreflight(estimate, 1, 400, 12_000, 2_000, 10_000, 1_000,
                CaptureSafetyState.SAFE, "Per-frame safe");
        SequencePreflight safe = SequencePreflightCalculator.calculate(safeFrame, 5);
        eq(3_500L, safe.requiredDiskBytes(), "sequence disk includes one temp frame plus all final files");
        eq(CaptureSafetyState.SAFE, safe.safetyState(), "five small frames are safe");

        CapturePreflight lowDisk = new CapturePreflight(estimate, 1, 400, 5_000, 2_000, 3_000, 1_000,
                CaptureSafetyState.SAFE, "Per-frame safe");
        eq(CaptureSafetyState.CANNOT_START, SequencePreflightCalculator.calculate(lowDisk, 5).safetyState(), "whole-session disk can block before frame one");

        CapturePreflight lowHeap = new CapturePreflight(estimate, 1, 400, 12_000, 2_000, 10_000, 50,
                CaptureSafetyState.SAFE, "Per-frame safe");
        eq(CaptureSafetyState.CANNOT_START, SequencePreflightCalculator.calculate(lowHeap, 2).safetyState(), "per-frame heap still blocks sequence");

        eq(CaptureSafetyState.HIGH_LOAD, SequencePreflightCalculator.calculate(safeFrame, 10).safetyState(), "ten frames are high load even when disk fits");
    }

    private static void timeBracket() {
        List<TimeBracketPlan.Frame> curated = TimeBracketPlan.curated().frames();
        eq(7, curated.size(), "curated time frame count");
        eq(TimeOfDayState.SUNRISE.label(), curated.get(0).label(), "sunrise first");
        eq(TimeOfDayState.NIGHT.label(), curated.get(6).label(), "night last");
        TimeBracketPlan custom = TimeBracketPlan.custom(23000, 1000, 500, true);
        eq(List.of(23000L, 23500L, 0L, 500L, 1000L), custom.frames().stream().map(TimeBracketPlan.Frame::dayTime).toList(), "wrapped custom range");
        expectThrows(() -> TimeBracketPlan.custom(0, 1000, 0, false), "zero time step rejected");
    }

    private static void eq(Object expected, Object actual, String name) {
        assertions++;
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError(name + ": expected=" + expected + " actual=" + actual);
    }

    private static void expectThrows(Runnable runnable, String name) {
        assertions++;
        try {
            runnable.run();
            throw new AssertionError(name + ": expected exception");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
