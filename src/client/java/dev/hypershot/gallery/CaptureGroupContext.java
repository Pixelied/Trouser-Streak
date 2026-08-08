package dev.hypershot.gallery;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Ephemeral mapping from a concrete CaptureManager capture ID to its higher-level camera sequence frame. */
public final class CaptureGroupContext {
    private final Map<String, GroupInfo> byCaptureId = new ConcurrentHashMap<>();

    public void attach(String captureId, GroupInfo info) {
        byCaptureId.put(Objects.requireNonNull(captureId), Objects.requireNonNull(info));
    }

    public GroupInfo find(String captureId) {
        return byCaptureId.get(captureId);
    }

    public GroupInfo remove(String captureId) {
        return byCaptureId.remove(captureId);
    }

    public void clear() {
        byCaptureId.clear();
    }

    public record GroupInfo(String groupId, String groupType, int groupIndex, int groupCount, String groupLabel) {
        public GroupInfo {
            if (groupId == null || groupId.isBlank()) throw new IllegalArgumentException("Group ID required");
            if (groupType == null || groupType.isBlank()) throw new IllegalArgumentException("Group type required");
            if (groupIndex < 1 || groupCount < 1 || groupIndex > groupCount) throw new IllegalArgumentException("Invalid group frame index");
            if (groupLabel == null || groupLabel.isBlank()) throw new IllegalArgumentException("Group label required");
        }
    }
}
