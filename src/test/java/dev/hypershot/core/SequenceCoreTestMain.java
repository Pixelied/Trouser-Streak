package dev.hypershot.core;

import dev.hypershot.core.camera.BurstPlan;
import dev.hypershot.core.camera.SequenceEstimate;
import dev.hypershot.core.camera.SequenceEstimateCalculator;
import dev.hypershot.core.camera.TimeBracketPlan;
import dev.hypershot.core.camera.TimeOfDayState;

import java.util.List;

public final class SequenceCoreTestMain {
    private static int assertions;
    public static void main(String[] args) {
        burstPlan(); sequenceEstimate(); timeBracket();
        System.out.println("HyperShot sequence core tests: PASS (" + assertions + " assertions)");
    }
    private static void burstPlan() {
        BurstPlan plan = new BurstPlan(5, 250);
        eq(5, plan.frameCount(), "burst count"); eq(250L, plan.intervalMillis(), "burst interval");
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
    private static void timeBracket() {
        List<TimeBracketPlan.Frame> curated = TimeBracketPlan.curated().frames();
        eq(7, curated.size(), "curated time frame count");
        eq(TimeOfDayState.SUNRISE.label(), curated.get(0).label(), "sunrise first");
        eq(TimeOfDayState.NIGHT.label(), curated.get(6).label(), "night last");
        TimeBracketPlan custom = TimeBracketPlan.custom(23000, 1000, 500, true);
        eq(List.of(23000L, 23500L, 0L, 500L, 1000L), custom.frames().stream().map(TimeBracketPlan.Frame::dayTime).toList(), "wrapped custom range");
        expectThrows(() -> TimeBracketPlan.custom(0, 1000, 0, false), "zero time step rejected");
    }
    private static void eq(Object expected,Object actual,String name){assertions++;if(!java.util.Objects.equals(expected,actual))throw new AssertionError(name+": expected="+expected+" actual="+actual);}
    private static void expectThrows(Runnable r,String name){assertions++;try{r.run();throw new AssertionError(name+": expected exception");}catch(IllegalArgumentException expected){}}
}
