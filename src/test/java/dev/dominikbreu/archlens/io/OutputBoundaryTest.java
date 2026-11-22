package dev.dominikbreu.archlens.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutputBoundaryTest {

    @TempDir
    Path tempDir;

    private Path work;
    private Path outside;

    @BeforeEach
    void createDirectories() throws IOException {
        work = Files.createDirectory(tempDir.resolve("work"));
        outside = Files.createDirectory(tempDir.resolve("outside"));
    }

    @Test
    void refusesADirectoryLinkOutOfTheWorkingDirectory() throws IOException {
        Files.createSymbolicLink(work.resolve("docs"), outside);

        assertRefused(Path.of("docs/out.md"), work);
    }

    @Test
    void refusesARelativeLinkOutOfTheWorkingDirectory() throws IOException {
        Files.createSymbolicLink(work.resolve("docs"), Path.of("../outside"));

        assertRefused(Path.of("docs/new/out.md"), work);
    }

    @Test
    void refusesADanglingLink() throws IOException {
        Files.createSymbolicLink(work.resolve("docs"), tempDir.resolve("missing"));

        assertRefused(Path.of("docs/out.md"), work);
    }

    @Test
    void refusesAFileLinkOutOfTheWorkingDirectory() throws IOException {
        Path secret = Files.writeString(outside.resolve("secret"), "secret");
        Files.createSymbolicLink(work.resolve("out.md"), secret);

        assertRefused(Path.of("out.md"), work);
    }

    @Test
    void refusesAnAbsolutePathThroughALinkInTheWorkingDirectory() throws IOException {
        Files.createSymbolicLink(work.resolve("docs"), outside);

        assertRefused(work.resolve("docs/out.md"), work);
    }

    @Test
    void refusesALinkReachedThroughAnAliasOfTheWorkingDirectory() throws IOException {
        Files.createSymbolicLink(work.resolve("docs"), outside);
        Path alias = Files.createSymbolicLink(tempDir.resolve("alias"), work);

        assertRefused(alias.resolve("docs/out.md"), work);
    }

    @Test
    void refusesALinkOutOfACheckoutBelowTheWorkingDirectory() throws IOException {
        Path checkout = Files.createDirectories(work.resolve("checkout"));
        Files.createDirectory(checkout.resolve(".git"));
        Path sibling = Files.createDirectory(work.resolve("sibling"));
        Files.createSymbolicLink(checkout.resolve("docs"), sibling);

        assertRefused(Path.of("checkout/docs/out.md"), work);
    }

    @Test
    void refusesALinkOutOfACheckoutAboveTheWorkingDirectory() throws IOException {
        Files.writeString(work.resolve(".git"), "gitdir: elsewhere");
        Path module = Files.createDirectory(work.resolve("module"));
        Files.createSymbolicLink(work.resolve("docs"), outside);

        assertRefused(Path.of("../docs/out.md"), module);
    }

    @Test
    void allowsALinkThatStaysInside() throws IOException {
        Path real = Files.createDirectory(work.resolve("real"));
        Files.createSymbolicLink(work.resolve("docs"), real);

        assertThat(OutputBoundary.resolve(Path.of("docs/out.md"), work, List.of()))
                .isEqualTo(work.resolve("docs/out.md"));
    }

    @Test
    void allowsAnExplicitPathOutsideEveryProject() throws IOException {
        Path alias = Files.createSymbolicLink(tempDir.resolve("alias"), outside);

        assertThat(OutputBoundary.resolve(alias.resolve("out.md"), work, List.of()))
                .isEqualTo(alias.resolve("out.md"));
    }

    @Test
    void resolvesParentSegmentsBeforeFollowingLinks() throws IOException {
        Files.createSymbolicLink(work.resolve("docs"), outside);

        assertThat(OutputBoundary.resolve(Path.of("docs/../out.md"), work, List.of()))
                .isEqualTo(work.resolve("out.md"));
    }

    private void assertRefused(Path requested, Path workingDirectory) {
        assertThatThrownBy(() -> OutputBoundary.resolve(requested, workingDirectory, List.of()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("symbolic link");
    }
}
