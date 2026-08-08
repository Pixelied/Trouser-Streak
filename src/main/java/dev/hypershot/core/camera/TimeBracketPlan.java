package dev.hypershot.core.camera;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record TimeBracketPlan(List<Frame> frames) {
    private static final long DAY = 24_000L;

    public TimeBracketPlan {
        if (frames == null || frames.isEmpty() || frames.size() > 100) throw new IllegalArgumentException("Time bracket must contain 1-100 frames");
        frames = List.copyOf(frames);
    }

    public static TimeBracketPlan curated() {
        return new TimeBracketPlan(Arrays.stream(TimeOfDayState.values())
                .map(state -> new Frame(state.label(), state.dayTime())).toList());
    }

    public static TimeBracketPlan custom(long start, long end, long step, boolean wrap) {
        if (step <= 0 || step > DAY) throw new IllegalArgumentException("Time step must be 1-24000 ticks");
        long normalizedStart = modDay(start);
        long normalizedEnd = modDay(end);
        long distance;
        if (wrap) {
            distance = Math.floorMod(normalizedEnd - normalizedStart, DAY);
        } else {
            if (normalizedEnd < normalizedStart) throw new IllegalArgumentException("End time precedes start without wrap");
            distance = normalizedEnd - normalizedStart;
        }
        if (distance % step != 0) throw new IllegalArgumentException("End time must be reachable by the selected step");
        long count = distance / step + 1;
        if (count > 100) throw new IllegalArgumentException("Time bracket exceeds 100 frames");
        List<Frame> frames = new ArrayList<>((int) count);
        for (int i = 0; i < count; i++) {
            long tick = wrap ? modDay(normalizedStart + i * step) : normalizedStart + i * step;
            frames.add(new Frame("Time " + tick, tick));
        }
        return new TimeBracketPlan(frames);
    }

    private static long modDay(long tick) {
        return Math.floorMod(tick, DAY);
    }

    public record Frame(String label, long dayTime) {
        public Frame {
            if (label == null || label.isBlank()) throw new IllegalArgumentException("Time frame label required");
            dayTime = modDay(dayTime);
        }
    }
}
