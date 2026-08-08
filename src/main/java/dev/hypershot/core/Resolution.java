package dev.hypershot.core;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Resolution(int width, int height) {
    private static final Pattern PATTERN = Pattern.compile("\\s*(\\d{1,9})\\s*[x×]\\s*(\\d{1,9})\\s*", Pattern.CASE_INSENSITIVE);

    public Resolution {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Resolution must be positive");
    }

    public static Resolution parse(String text) {
        Objects.requireNonNull(text, "text");
        Matcher matcher = PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        if (!matcher.matches()) throw new IllegalArgumentException("Expected WIDTHxHEIGHT");
        try {
            return new Resolution(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Resolution exceeds supported integer range", ex);
        }
    }

    public long pixels() { return CheckedMath.multiply(width, height); }

    public String reducedAspectRatio() {
        int gcd = gcd(width, height);
        return (width / gcd) + ":" + (height / gcd);
    }

    private static int gcd(int a, int b) {
        while (b != 0) { int t = a % b; a = b; b = t; }
        return Math.abs(a);
    }
}
