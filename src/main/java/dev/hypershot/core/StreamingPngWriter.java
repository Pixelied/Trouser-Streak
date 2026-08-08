package dev.hypershot.core;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** A bounded-memory, scanline-oriented 8-bit RGBA PNG encoder. */
public final class StreamingPngWriter implements Closeable {
    private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private final DataOutputStream output;
    private final Deflater deflater;
    private final DeflaterOutputStream compressed;
    private final int width;
    private final int height;
    private final int rowBytes;
    private int rowsWritten;
    private boolean closed;

    public StreamingPngWriter(Path path, int width, int height, int compressionLevel) throws IOException {
        this(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), width, height, compressionLevel);
    }

    public StreamingPngWriter(OutputStream stream, int width, int height, int compressionLevel) throws IOException {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("PNG dimensions must be positive");
        if (compressionLevel < 0 || compressionLevel > 9) throw new IllegalArgumentException("PNG compression must be 0..9");
        this.width = width;
        this.height = height;
        this.rowBytes = CheckedMath.checkedInt(CheckedMath.multiply(width, 4), "PNG row bytes");
        this.output = new DataOutputStream(new BufferedOutputStream(Objects.requireNonNull(stream, "stream"), 128 * 1024));
        output.write(SIGNATURE);
        byte[] ihdr = new byte[13];
        putInt(ihdr, 0, width);
        putInt(ihdr, 4, height);
        ihdr[8] = 8; // bit depth
        ihdr[9] = 6; // RGBA
        ihdr[10] = 0;
        ihdr[11] = 0;
        ihdr[12] = 0;
        writeChunk(output, "IHDR", ihdr, 0, ihdr.length);
        writeChunk(output, "sRGB", new byte[] {0}, 0, 1);
        this.deflater = new Deflater(compressionLevel, false);
        this.compressed = new DeflaterOutputStream(new IdatChunkOutputStream(output, 64 * 1024), deflater, 64 * 1024, true);
    }

    public void writeText(String key, String value) throws IOException {
        ensureOpen();
        if (rowsWritten != 0) throw new IllegalStateException("PNG text must be written before image rows");
        String safeKey = Objects.requireNonNull(key, "key").replace('\0', '_');
        if (safeKey.isBlank() || safeKey.length() > 79) throw new IllegalArgumentException("PNG text keyword length");
        byte[] k = safeKey.getBytes(StandardCharsets.ISO_8859_1);
        byte[] v = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.ISO_8859_1);
        byte[] data = new byte[k.length + 1 + v.length];
        System.arraycopy(k, 0, data, 0, k.length);
        System.arraycopy(v, 0, data, k.length + 1, v.length);
        writeChunk(output, "tEXt", data, 0, data.length);
    }

    public void writeRow(byte[] rgba) throws IOException {
        ensureOpen();
        Objects.requireNonNull(rgba, "rgba");
        if (rgba.length != rowBytes) throw new IllegalArgumentException("Expected " + rowBytes + " bytes, got " + rgba.length);
        if (rowsWritten >= height) throw new IllegalStateException("Too many PNG rows");
        compressed.write(0); // PNG filter: None
        compressed.write(rgba);
        rowsWritten++;
    }

    public int rowsWritten() { return rowsWritten; }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        IOException failure = null;
        try {
            if (rowsWritten != height) throw new IOException("Incomplete PNG: wrote " + rowsWritten + " of " + height + " rows");
            compressed.finish();
            compressed.close();
            writeChunk(output, "IEND", new byte[0], 0, 0);
            output.flush();
        } catch (IOException e) {
            failure = e;
        } finally {
            deflater.end();
            try { output.close(); } catch (IOException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        }
        if (failure != null) throw failure;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("PNG writer is closed");
    }

    private static void putInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    private static void writeChunk(DataOutputStream out, String type, byte[] data, int offset, int length) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        if (typeBytes.length != 4) throw new IllegalArgumentException("PNG chunk type");
        out.writeInt(length);
        out.write(typeBytes);
        out.write(data, offset, length);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data, offset, length);
        out.writeInt((int) crc.getValue());
    }

    private static final class IdatChunkOutputStream extends OutputStream {
        private final DataOutputStream target;
        private final byte[] buffer;
        private int count;
        private boolean closed;

        private IdatChunkOutputStream(DataOutputStream target, int chunkSize) {
            this.target = target;
            this.buffer = new byte[chunkSize];
        }

        @Override public void write(int value) throws IOException {
            if (count == buffer.length) flushChunk();
            buffer[count++] = (byte) value;
        }

        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            while (length > 0) {
                int copied = Math.min(length, buffer.length - count);
                System.arraycopy(bytes, offset, buffer, count, copied);
                count += copied;
                offset += copied;
                length -= copied;
                if (count == buffer.length) flushChunk();
            }
        }

        @Override public void flush() throws IOException { flushChunk(); }
        @Override public void close() throws IOException { if (!closed) { closed = true; flushChunk(); } }

        private void flushChunk() throws IOException {
            if (count == 0) return;
            writeChunk(target, "IDAT", buffer, 0, count);
            count = 0;
        }
    }
}
