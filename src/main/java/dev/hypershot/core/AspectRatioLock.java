package dev.hypershot.core;

public final class AspectRatioLock {
    private AspectRatioLock() {}

    public static Resolution lockWidth(int width, Resolution ratio) {
        if (width <= 0) throw new IllegalArgumentException("width");
        int height = Math.max(1, CheckedMath.checkedInt(Math.round(width * (double) ratio.height() / ratio.width()), "height"));
        return new Resolution(width, height);
    }

    public static Resolution lockHeight(int height, Resolution ratio) {
        if (height <= 0) throw new IllegalArgumentException("height");
        int width = Math.max(1, CheckedMath.checkedInt(Math.round(height * (double) ratio.width() / ratio.height()), "width"));
        return new Resolution(width, height);
    }
}
