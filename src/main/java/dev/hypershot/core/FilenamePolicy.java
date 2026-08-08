package dev.hypershot.core;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class FilenamePolicy {
    private static final Pattern INVALID = Pattern.compile("[\\x00-\\x1f<>:\"/\\\\|?*]");
    private static final Set<String> RESERVED = Set.of("CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private FilenamePolicy() {}

    public static String sanitizeStem(String input) {
        String normalized = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFKC).trim();
        normalized = INVALID.matcher(normalized).replaceAll("_").replaceAll("\\s+", " ");
        while (normalized.endsWith(".") || normalized.endsWith(" ")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.isBlank()) normalized = "screenshot";
        if (RESERVED.contains(normalized.toUpperCase(Locale.ROOT))) normalized += "_";
        if (normalized.length() > 180) normalized = normalized.substring(0, 180).stripTrailing();
        return normalized;
    }

    public static String expand(String template, Map<String, String> tokens) {
        String value = template;
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return sanitizeStem(value.replaceAll("\\{[^{}]+}", ""));
    }
}
