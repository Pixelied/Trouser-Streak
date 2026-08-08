package dev.hypershot.gallery;

import dev.hypershot.core.AtomicOutput;
import dev.hypershot.core.FilenamePolicy;
import dev.hypershot.util.HyperShotPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class GalleryFileService {
    private final HyperShotPaths paths;

    public GalleryFileService(HyperShotPaths paths) { this.paths = paths; }

    public DeletedCapture delete(CaptureRecord record) throws IOException {
        List<Move> moves = new ArrayList<>();
        moveToTrash(record.image(), moves);
        if (record.metadata() != null) moveToTrash(record.metadata(), moves);
        if (record.thumbnail() != null) moveToTrash(record.thumbnail(), moves);
        return new DeletedCapture(record, moves);
    }

    public void restore(DeletedCapture deleted) throws IOException {
        for (Move move : deleted.moves.reversed()) {
            if (!Files.exists(move.trashed)) continue;
            Files.createDirectories(move.original.toAbsolutePath().getParent());
            Files.move(move.trashed, move.original, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public CaptureRecord rename(CaptureRecord record, String requestedStem) throws IOException {
        String stem = FilenamePolicy.sanitizeStem(requestedStem);
        String extension = extension(record.filename);
        Path destination = AtomicOutput.uniqueSibling(record.image().resolveSibling(stem + extension));
        Files.move(record.image(), destination);
        record.imagePath = destination.toAbsolutePath().normalize().toString();
        record.filename = destination.getFileName().toString();
        return record;
    }

    private void moveToTrash(Path original, List<Move> moves) throws IOException {
        if (!Files.exists(original)) return;
        Path destination = AtomicOutput.uniqueSibling(paths.trash().resolve(original.getFileName()));
        Files.move(original, destination);
        moves.add(new Move(original, destination));
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot);
    }

    public record DeletedCapture(CaptureRecord record, List<Move> moves) {}
    public record Move(Path original, Path trashed) {}
}
