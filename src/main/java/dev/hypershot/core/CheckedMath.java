package dev.hypershot.core;

public final class CheckedMath {
    private CheckedMath() {}

    public static long multiply(long a, long b) {
        return Math.multiplyExact(a, b);
    }

    public static long add(long a, long b) {
        return Math.addExact(a, b);
    }

    public static long bytesForPixels(int width, int height, int bytesPerPixel) {
        if (width <= 0 || height <= 0 || bytesPerPixel <= 0) {
            throw new IllegalArgumentException("Dimensions and bytes per pixel must be positive");
        }
        return multiply(multiply(width, height), bytesPerPixel);
    }

    public static int checkedInt(long value, String label) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new ArithmeticException(label + " exceeds signed int range: " + value);
        }
        return (int) value;
    }
}
