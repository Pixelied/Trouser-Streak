package dev.hypershot.core;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Positional RGBA spool that supports out-of-order tile writes without a full heap image. */
public final class DiskBackedRgbaSurface implements Closeable {
    private final Path path;
    private final int width;
    private final int height;
    private final int rowBytes;
    private final long byteSize;
    private final FileChannel channel;
    private boolean closed;

    public DiskBackedRgbaSurface(Path path, int width, int height) throws IOException {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("surface dimensions");
        this.path = Objects.requireNonNull(path, "path");
        this.width = width;
        this.height = height;
        this.rowBytes = CheckedMath.checkedInt(CheckedMath.multiply(width, 4), "surface row bytes");
        this.byteSize = CheckedMath.bytesForPixels(width, height, 4);
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        this.channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE);
        if (byteSize > 0) {
            channel.position(byteSize - 1);
            channel.write(ByteBuffer.wrap(new byte[] {0}));
        }
    }

    public Path path() { return path; }
    public long byteSize() { return byteSize; }

    public synchronized void writeTile(Tile tile, byte[] renderedRgba) throws IOException {
        ensureOpen();
        Objects.requireNonNull(tile, "tile");
        Objects.requireNonNull(renderedRgba, "renderedRgba");
        long expected = CheckedMath.bytesForPixels(tile.renderWidth(), tile.renderHeight(), 4);
        if (renderedRgba.length != expected) throw new IllegalArgumentException("Tile buffer length mismatch");
        if (tile.contentX() < 0 || tile.contentY() < 0 || tile.contentX() + tile.contentWidth() > width || tile.contentY() + tile.contentHeight() > height) {
            throw new IllegalArgumentException("Tile content outside surface");
        }
        int sourceStride = CheckedMath.checkedInt(CheckedMath.multiply(tile.renderWidth(), 4), "tile stride");
        int contentBytes = CheckedMath.checkedInt(CheckedMath.multiply(tile.contentWidth(), 4), "tile content bytes");
        for (int row = 0; row < tile.contentHeight(); row++) {
            int sourceY = tile.cropTop() + row;
            int sourceOffset = sourceY * sourceStride + tile.cropLeft() * 4;
            long destination = CheckedMath.add(CheckedMath.multiply(tile.contentY() + row, rowBytes), CheckedMath.multiply(tile.contentX(), 4));
            writeFully(ByteBuffer.wrap(renderedRgba, sourceOffset, contentBytes), destination);
        }
    }

    public synchronized void writeRow(int y, byte[] source) throws IOException {
        ensureOpen();
        if (y < 0 || y >= height) throw new IndexOutOfBoundsException("row " + y);
        if (source.length != rowBytes) throw new IllegalArgumentException("row buffer length");
        writeFully(ByteBuffer.wrap(source), CheckedMath.multiply(y, rowBytes));
    }

    public synchronized void readRow(int y, byte[] target) throws IOException {
        ensureOpen();
        if (y < 0 || y >= height) throw new IndexOutOfBoundsException("row " + y);
        if (target.length != rowBytes) throw new IllegalArgumentException("row buffer length");
        readFully(ByteBuffer.wrap(target), CheckedMath.multiply(y, rowBytes));
    }

    public synchronized void encodePng(Path output, int compressionLevel) throws IOException {
        ensureOpen();
        byte[] row = new byte[rowBytes];
        try (StreamingPngWriter writer = new StreamingPngWriter(output, width, height, compressionLevel)) {
            for (int y = 0; y < height; y++) {
                readRow(y, row);
                writer.writeRow(row);
            }
        }
    }

    public synchronized void force() throws IOException { ensureOpen(); channel.force(false); }

    @Override public synchronized void close() throws IOException {
        if (!closed) { closed = true; channel.close(); }
    }

    private void writeFully(ByteBuffer buffer, long position) throws IOException {
        while (buffer.hasRemaining()) {
            int written = channel.write(buffer, position);
            if (written <= 0) throw new IOException("Unable to advance RGBA spool write");
            position += written;
        }
    }

    private void readFully(ByteBuffer buffer, long position) throws IOException {
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position);
            if (read < 0) throw new EOFException("Unexpected end of RGBA spool");
            if (read == 0) throw new IOException("Unable to advance RGBA spool read");
            position += read;
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("surface closed");
    }
}
