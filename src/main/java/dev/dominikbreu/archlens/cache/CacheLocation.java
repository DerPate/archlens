package dev.dominikbreu.archlens.cache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * Chooses the default cache directory: private to the user, with one subdirectory per working
 * directory so that a restart in the same directory resumes its workspace.
 */
final class CacheLocation {
    /** Environment variable overriding the cache root. */
    private static final String OVERRIDE = "ARCHLENS_CACHE_DIR";

    /** Directory name below the user's cache directory. */
    private static final String APPLICATION = "archlens";

    private CacheLocation() {}

    /**
     * Resolves the cache directory for this process.
     *
     * @return absolute cache directory
     */
    static Path current() {
        return resolve(System.getenv(), System.getProperty("user.home"), Path.of(""));
    }

    /**
     * Resolves the cache directory for an environment.
     *
     * @param environment environment variables
     * @param userHome user home directory, possibly unknown
     * @param workingDirectory directory the server was started in
     * @return absolute cache directory
     */
    static Path resolve(Map<String, String> environment, String userHome, Path workingDirectory) {
        return root(environment, userHome).resolve(digest(canonical(workingDirectory)));
    }

    /** Picks the root from the override, the platform's cache variable, or the home directory. */
    private static Path root(Map<String, String> environment, String userHome) {
        String override = environment.get(OVERRIDE);
        if (override != null && !override.isBlank()) {
            return absolute(override)
                    .orElseThrow(() -> new IllegalArgumentException(OVERRIDE + " must be an absolute path"));
        }
        return absolute(environment.get("XDG_CACHE_HOME"))
                .or(() -> absolute(environment.get("LOCALAPPDATA")))
                .or(() -> absolute(userHome).map(home -> home.resolve(".cache")))
                .map(base -> base.resolve(APPLICATION))
                .orElseGet(CacheLocation::privateTemporaryDirectory);
    }

    /** Parses a path, accepting only absolute ones. */
    private static Optional<Path> absolute(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            Path path = Path.of(value);
            return path.isAbsolute() ? Optional.of(path.normalize()) : Optional.empty();
        } catch (InvalidPathException _) {
            return Optional.empty();
        }
    }

    /** Without a home there is nowhere durable and private, so the cache lasts for this run only. */
    private static Path privateTemporaryDirectory() {
        try {
            return Files.createTempDirectory(APPLICATION + "-cache-");
        } catch (IOException error) {
            throw new IllegalStateException("No cache directory available; set " + OVERRIDE, error);
        }
    }

    /** Resolves symlinks where possible, so one directory has one cache. */
    private static String canonical(Path directory) {
        try {
            return directory.toRealPath().toString();
        } catch (IOException _) {
            return directory.toAbsolutePath().normalize().toString();
        }
    }

    /** Shortens a directory to a stable directory name. */
    private static String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is not available", error);
        }
    }
}
