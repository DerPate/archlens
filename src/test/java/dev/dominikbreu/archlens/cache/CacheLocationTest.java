package dev.dominikbreu.archlens.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CacheLocationTest {
    @TempDir
    Path temp;

    @Test
    void defaultsToTheUserCacheOutsideTheWorkingDirectory() {
        Path project = temp.resolve("project");

        Path cache = CacheLocation.resolve(Map.of(), temp.resolve("home").toString(), project);

        assertThat(cache).startsWithRaw(temp.resolve("home/.cache/archlens"));
        assertThat(cache.startsWith(project)).isFalse();
    }

    @Test
    void honoursXdgCacheHome() {
        Path xdg = temp.resolve("xdg");

        assertThat(CacheLocation.resolve(Map.of("XDG_CACHE_HOME", xdg.toString()), temp.toString(), temp))
                .startsWithRaw(xdg.resolve("archlens"));
    }

    @Test
    void usesLocalAppDataWhenSet() {
        Path local = temp.resolve("local");

        assertThat(CacheLocation.resolve(Map.of("LOCALAPPDATA", local.toString()), temp.toString(), temp))
                .startsWithRaw(local.resolve("archlens"));
    }

    @Test
    void explicitDirectoryWins() {
        Path explicit = temp.resolve("explicit");
        Map<String, String> environment = Map.of(
                "ARCHLENS_CACHE_DIR", explicit.toString(),
                "XDG_CACHE_HOME", temp.resolve("xdg").toString());

        assertThat(CacheLocation.resolve(environment, temp.toString(), temp)).startsWithRaw(explicit);
    }

    @Test
    void rejectsARelativeExplicitDirectory() {
        assertThatThrownBy(() -> CacheLocation.resolve(Map.of("ARCHLENS_CACHE_DIR", "cache"), temp.toString(), temp))
                .hasMessageContaining("absolute");
    }

    @Test
    void ignoresARelativeXdgDirectoryAsTheSpecificationRequires() {
        Path home = temp.resolve("home");

        assertThat(CacheLocation.resolve(Map.of("XDG_CACHE_HOME", "cache"), home.toString(), temp))
                .startsWithRaw(home.resolve(".cache"));
    }

    @Test
    void neverFallsBackToARelativePathWithoutAHome() throws Exception {
        Path cache = CacheLocation.resolve(Map.of(), "?", temp);
        Files.delete(cache.getParent()); // the empty temporary root this fallback creates

        assertThat(cache.isAbsolute()).isTrue();
        assertThat(cache.startsWith(Path.of("").toAbsolutePath())).isFalse();
    }

    @Test
    void separatesWorkingDirectoriesAndIsStableForEach() {
        Path first = CacheLocation.resolve(Map.of(), temp.toString(), temp.resolve("a"));

        assertThat(CacheLocation.resolve(Map.of(), temp.toString(), temp.resolve("a")))
                .isEqualTo(first);
        assertThat(CacheLocation.resolve(Map.of(), temp.toString(), temp.resolve("b")))
                .isNotEqualTo(first);
    }

    @Test
    void mapsASymlinkedWorkingDirectoryToTheSameCache() throws Exception {
        Path real = Files.createDirectories(temp.resolve("real"));
        Path link = Files.createSymbolicLink(temp.resolve("link"), real);

        assertThat(CacheLocation.resolve(Map.of(), temp.toString(), link))
                .isEqualTo(CacheLocation.resolve(Map.of(), temp.toString(), real));
    }
}
