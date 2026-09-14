package dev.dominikbreu.archlens.io;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes one or more files without ever leaving a target truncated or half-updated: each file is
 * staged as a temp file in its own parent directory, then promoted with an atomic move. If any
 * promotion in a {@link #writeAll} call fails, every file already promoted in that call is rolled
 * back to what it held before the call (or deleted, if it didn't exist yet).
 */
public final class AtomicFileWriter {
    private final FilePromoter promoter;

    /** Creates a writer that promotes staged files with atomic moves when supported. */
    public AtomicFileWriter() {
        this(AtomicFileWriter::promote);
    }

    /**
     * Creates a writer with an injectable file promoter.
     *
     * @param promoter file promotion strategy
     */
    AtomicFileWriter(FilePromoter promoter) {
        this.promoter = promoter;
    }

    /**
     * Writes a single file.
     *
     * @param target file to write
     * @param content bytes to write
     * @throws IOException when staging or promotion fails
     */
    public void write(Path target, byte[] content) throws IOException {
        writeAll(Map.of(target, content));
    }

    /**
     * Writes every file in {@code content}, all-or-nothing: if any promotion fails, every file
     * already promoted in this call is rolled back before the failure is rethrown.
     *
     * @param content target path to file bytes
     * @throws IOException when staging fails, or when promotion fails (after rollback)
     */
    public void writeAll(Map<Path, byte[]> content) throws IOException {
        Map<Path, Optional<byte[]>> snapshots = new LinkedHashMap<>();
        Map<Path, Path> staged = new LinkedHashMap<>();
        List<Path> promoted = new ArrayList<>();
        List<Path> temps = new ArrayList<>();
        try {
            for (Map.Entry<Path, byte[]> entry : content.entrySet()) {
                Path target = entry.getKey();
                snapshots.put(target, snapshot(target));
                Path parent = requireParent(target);
                Path fileName = requireFileName(target);
                Files.createDirectories(parent);
                Path temp = Files.createTempFile(parent, fileName.toString(), ".tmp");
                temps.add(temp);
                Files.write(temp, entry.getValue());
                staged.put(target, temp);
            }
            for (Map.Entry<Path, Path> entry : staged.entrySet()) {
                promoter.move(entry.getValue(), entry.getKey());
                promoted.add(entry.getKey());
            }
        } catch (IOException promotionFailure) {
            restore(promoted, snapshots, temps, promotionFailure);
        } finally {
            for (Path temp : temps) {
                Files.deleteIfExists(temp);
            }
        }
    }

    private static Optional<byte[]> snapshot(Path path) throws IOException {
        return Files.exists(path) ? Optional.of(Files.readAllBytes(path)) : Optional.empty();
    }

    private static void restore(
            List<Path> promoted, Map<Path, Optional<byte[]>> snapshots, List<Path> temps, IOException cause)
            throws IOException {
        List<Path> unrestored = new ArrayList<>();
        for (int index = promoted.size() - 1; index >= 0; index--) {
            Path target = promoted.get(index);
            try {
                Optional<byte[]> content = snapshots.get(target);
                if (content.isPresent()) {
                    Path fileName = requireFileName(target);
                    Path temp = Files.createTempFile(requireParent(target), fileName.toString(), ".restore");
                    temps.add(temp);
                    Files.write(temp, content.get());
                    promote(temp, target);
                } else {
                    Files.deleteIfExists(target);
                }
            } catch (IOException restoreFailure) {
                unrestored.add(target);
                cause.addSuppressed(restoreFailure);
            }
        }
        if (!unrestored.isEmpty()) {
            IOException failure = new IOException(
                    "Promotion failed and restoration failed for " + unrestored + ": " + cause.getMessage(), cause);
            throw failure;
        }
        throw cause;
    }

    private static void promote(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path requireParent(Path path) {
        Path parent = path.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Path must have a parent: " + path);
        }
        return parent;
    }

    private static Path requireFileName(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("Path must have a file name: " + path);
        }
        return fileName;
    }
}

@FunctionalInterface
interface FilePromoter {
    void move(Path source, Path target) throws IOException;
}
