package dev.hypershot.core;

public enum ResolutionPreset {
    NATIVE("Native", null),
    UHD_4K("4K UHD", new Resolution(3840, 2160)),
    UHD_8K("8K UHD", new Resolution(7680, 4320)),
    UHD_16K("16K UHD", new Resolution(15360, 8640)),
    UHD_32K("32K UHD", new Resolution(30720, 17280)),
    CUSTOM("Custom", null);

    private final String displayName;
    private final Resolution fixedResolution;

    ResolutionPreset(String displayName, Resolution fixedResolution) {
        this.displayName = displayName;
        this.fixedResolution = fixedResolution;
    }

    public String displayName() {
        return displayName;
    }

    public Resolution fixedResolution() {
        return fixedResolution;
    }
}
