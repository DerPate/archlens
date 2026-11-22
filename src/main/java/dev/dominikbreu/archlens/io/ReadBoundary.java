package dev.dominikbreu.archlens.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Limits what indexing reads to the indexed projects, whatever their symbolic links and module
 * declarations point at.
 *
 * <p>A project is its root plus the Git checkout enclosing it. A checkout at or above the user's home
 * directory does not count, so that a versioned home directory does not become one project.
 *
 * <p>The boundary applies inside {@link #within}. Every reader of project files asks {@link #allows}
 * before it opens or descends into a path.
 */
public final class ReadBoundary {
    private static final Logger LOG = LoggerFactory.getLogger(ReadBoundary.class);

    private static final ScopedValue<ReadBoundary> CURRENT = ScopedValue.newInstance();

    private final Set<Path> areas;

    private ReadBoundary(Set<Path> areas) {
        this.areas = Set.copyOf(areas);
    }

    /**
     * Runs an action that may only read inside the given projects.
     *
     * @param projectRoots roots the caller asked to index
     * @param action action to run; its reads must happen on the calling thread
     * @param <T> action result type
     * @return the action's result
     * @throws IllegalArgumentException if a root leaves its Git checkout through a symbolic link
     */
    public static <T> T within(Collection<String> projectRoots, Supplier<T> action) {
        Path home = home();
        Set<Path> areas = new HashSet<>();
        for (String root : projectRoots) {
            addProject(new File(root).toPath(), home, areas);
        }
        return ScopedValue.where(CURRENT, new ReadBoundary(areas)).call(action::get);
    }

    /**
     * Tells whether a path may be read. Outside {@link #within} there is no boundary.
     *
     * @param file file or directory about to be read
     * @return {@code false} if it resolves to a place outside the projects, or cannot be resolved
     */
    public static boolean allows(File file) {
        return !CURRENT.isBound() || CURRENT.get().contains(file);
    }

    private boolean contains(File file) {
        if (!file.exists()) return false;
        try {
            // Not normalized first: "link/.." must mean what it means when the file is opened.
            Path real = file.toPath().toRealPath();
            if (areas.stream().anyMatch(real::startsWith)) return true;
            LOG.warn("Skipping {}: it resolves outside the indexed project", file);
        } catch (IOException error) {
            LOG.warn("Skipping {}: it cannot be resolved", file, error);
        }
        return false;
    }

    /** Adds a root's real path and its enclosing checkout. A missing root is left to project detection. */
    private static void addProject(Path root, Path home, Set<Path> areas) {
        if (!Files.exists(root)) return;
        try {
            Path real = root.toRealPath();
            Path named = root.toAbsolutePath().normalize();
            for (Path above = named.getParent(); above != null; above = above.getParent()) {
                if (!isCheckout(above)) continue;
                Path checkout = above.toRealPath();
                if (!holdsHome(checkout, home) && !real.startsWith(checkout)) {
                    throw new IllegalArgumentException(
                            "Refusing " + root + ": a symbolic link leads out of the checkout " + above);
                }
            }
            areas.add(real);
            for (Path directory = real; directory != null && !holdsHome(directory, home); ) {
                if (isCheckout(directory)) {
                    areas.add(directory);
                    break;
                }
                directory = directory.getParent();
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("Cannot resolve project root: " + root, error);
        }
    }

    private static boolean isCheckout(Path directory) {
        return Files.exists(directory.resolve(".git"), LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean holdsHome(Path directory, Path home) {
        return home != null && home.startsWith(directory);
    }

    /** Returns the real home directory, or {@code null} when there is none. */
    private static Path home() {
        String value = System.getProperty("user.home", "");
        if (value.isBlank()) return null;
        File home = new File(value);
        if (!home.isAbsolute() || !home.isDirectory()) return null;
        try {
            return home.toPath().toRealPath();
        } catch (IOException error) {
            LOG.warn("Cannot resolve the home directory {}", home, error);
            return home.toPath().normalize();
        }
    }
}
