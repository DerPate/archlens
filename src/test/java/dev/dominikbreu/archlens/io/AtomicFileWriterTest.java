package dev.dominikbreu.archlens.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFileWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writeCreatesFileAndMissingParentDirectories() throws Exception {
        Path target = tempDir.resolve("nested/dir/output.txt");

        new AtomicFileWriter().write(target, bytes("hello"));

        assertThat(target).hasContent("hello");
    }

    @Test
    void writeOverwritesExistingContent() throws Exception {
        Path target = tempDir.resolve("output.txt");
        Files.writeString(target, "old");

        new AtomicFileWriter().write(target, bytes("new"));

        assertThat(target).hasContent("new");
    }

    @Test
    void writeAllWritesEveryFile() throws Exception {
        Path first = tempDir.resolve("a.txt");
        Path second = tempDir.resolve("nested/b.txt");

        new AtomicFileWriter().writeAll(Map.of(first, bytes("a"), second, bytes("b")));

        assertThat(first).hasContent("a");
        assertThat(second).hasContent("b");
    }

    @Test
    void writeAllRestoresEveryFileWhenALaterPromotionFails() throws Exception {
        Path first = tempDir.resolve("a.txt");
        Path second = tempDir.resolve("b.txt");
        new AtomicFileWriter().writeAll(Map.of(first, bytes("original-a"), second, bytes("original-b")));

        AtomicInteger moves = new AtomicInteger();
        AtomicFileWriter failing = new AtomicFileWriter((source, target) -> {
            if (moves.incrementAndGet() == 2) {
                throw new IOException("injected promotion failure");
            }
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        });

        Map<Path, byte[]> update = new LinkedHashMap<>();
        update.put(first, bytes("updated-a"));
        update.put(second, bytes("updated-b"));

        assertThatThrownBy(() -> failing.writeAll(update)).hasMessageContaining("injected promotion failure");
        assertThat(first).hasContent("original-a");
        assertThat(second).hasContent("original-b");
    }

    @Test
    void writeAllDeletesFilesThatDidNotExistBeforeWhenALaterPromotionFails() throws Exception {
        Path first = tempDir.resolve("a.txt");
        Path second = tempDir.resolve("b.txt");

        AtomicInteger moves = new AtomicInteger();
        AtomicFileWriter failing = new AtomicFileWriter((source, target) -> {
            if (moves.incrementAndGet() == 2) {
                throw new IOException("injected promotion failure");
            }
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        });

        Map<Path, byte[]> update = new LinkedHashMap<>();
        update.put(first, bytes("a"));
        update.put(second, bytes("b"));

        assertThatThrownBy(() -> failing.writeAll(update)).hasMessageContaining("injected promotion failure");
        assertThat(first).doesNotExist();
        assertThat(second).doesNotExist();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
