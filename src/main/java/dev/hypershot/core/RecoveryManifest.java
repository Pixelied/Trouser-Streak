package dev.hypershot.core;

import java.nio.file.Path;
import java.util.BitSet;
import java.util.Objects;

public final class RecoveryManifest {
    private final String captureId;
    private final Path temporaryPath;
    private final int columns;
    private final int rows;
    private final BitSet completed;

    private RecoveryManifest(String captureId, Path temporaryPath, int columns, int rows, BitSet completed) {
        this.captureId = Objects.requireNonNull(captureId);
        this.temporaryPath = Objects.requireNonNull(temporaryPath);
        if (columns <= 0 || rows <= 0) throw new IllegalArgumentException("grid");
        this.columns = columns;
        this.rows = rows;
        this.completed = (BitSet) completed.clone();
    }

    public static RecoveryManifest create(String captureId, Path temporaryPath, int columns, int rows) {
        return new RecoveryManifest(captureId, temporaryPath, columns, rows, new BitSet(columns * rows));
    }

    public RecoveryManifest withCompleted(int index) {
        if (index < 0 || index >= columns * rows) throw new IllegalArgumentException("tile index");
        BitSet copy = (BitSet) completed.clone();
        copy.set(index);
        return new RecoveryManifest(captureId, temporaryPath, columns, rows, copy);
    }

    public boolean isCompleted(int index) { return completed.get(index); }

    public String toJson() {
        return "{\"version\":1,\"captureId\":\"" + escape(captureId) + "\",\"temporaryPath\":\"" +
                escape(temporaryPath.toString()) + "\",\"columns\":" + columns + ",\"rows\":" + rows +
                ",\"completed\":\"" + bytesToHex(completed.toByteArray()) + "\"}";
    }

    public static RecoveryManifest fromJson(String json) {
        String id = field(json, "captureId");
        String path = field(json, "temporaryPath");
        int columns = Integer.parseInt(number(json, "columns"));
        int rows = Integer.parseInt(number(json, "rows"));
        BitSet completed = BitSet.valueOf(hexToBytes(field(json, "completed")));
        return new RecoveryManifest(id, Path.of(path), columns, rows, completed);
    }

    private static String field(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) throw new IllegalArgumentException("Missing " + key);
        start += needle.length();
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) { result.append(c); escaped = false; }
            else if (c == '\\') escaped = true;
            else if (c == '"') return result.toString();
            else result.append(c);
        }
        throw new IllegalArgumentException("Unterminated " + key);
    }

    private static String number(String json, String key) {
        String needle = "\"" + key + "\":";
        int start = json.indexOf(needle);
        if (start < 0) throw new IllegalArgumentException("Missing " + key);
        start += needle.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        return json.substring(start, end);
    }

    private static String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String bytesToHex(byte[] bytes) { StringBuilder b = new StringBuilder(); for (byte value : bytes) b.append(String.format("%02x", value)); return b.toString(); }
    private static byte[] hexToBytes(String hex) { if ((hex.length() & 1) != 0) throw new IllegalArgumentException("hex"); byte[] out = new byte[hex.length()/2]; for (int i=0;i<out.length;i++) out[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16); return out; }

    @Override public boolean equals(Object o) { return o instanceof RecoveryManifest other && captureId.equals(other.captureId) && temporaryPath.equals(other.temporaryPath) && columns == other.columns && rows == other.rows && completed.equals(other.completed); }
    @Override public int hashCode() { return Objects.hash(captureId, temporaryPath, columns, rows, completed); }
}
