package dev.dominikbreu.archlens.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Keeps generated files inside the project their path names, whatever symbolic links the project
 * contains.
 */
public final class OutputBoundary {
    private OutputBoundary() {}

    /**
     * Resolves an output path against the working directory and checks every project it passes
     * through: the working directory, the given roots, and any Git checkout. A path outside all of
     * them is the caller's explicit choice and is returned unchanged.
     *
     * @param requested requested output path
     * @param projectRoots roots of the indexed projects
     * @return absolute, normalized path to write
     * @throws IOException if a symbolic link leads out of a project, or cannot be resolved
     */
    public static Path resolve(Path requested, Collection<String> projectRoots) throws IOException {
        return resolve(requested, Path.of("").toAbsolutePath(), projectRoots);
    }

    /** Resolves as {@link #resolve(Path, Collection)} does, for an explicit working directory. */
    static Path resolve(Path requested, Path workingDirectory, Collection<String> projectRoots) throws IOException {
        Path target = workingDirectory.resolve(requested).toAbsolutePath().normalize();
        Set<Path> roots = new HashSet<>();
        roots.add(workingDirectory.toRealPath());
        for (String root : projectRoots) {
            addExistingDirectory(root, roots);
        }
        Path destination = physical(target);
        // Compared by real path, so that an alias of a project is still that project.
        for (Path ancestor = target.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (!Files.isDirectory(ancestor)) continue;
            Path real = ancestor.toRealPath();
            boolean project = roots.contains(real) || Files.exists(ancestor.resolve(".git"), LinkOption.NOFOLLOW_LINKS);
            if (project && !destination.startsWith(real)) {
                throw leaves(target, ancestor);
            }
        }
        return target;
    }

    /**
     * Checks that a path below a directory stays there once symbolic links are resolved.
     *
     * @param directory directory that must contain the path
     * @param path path to read or write
     * @throws IOException if a symbolic link leads out of the directory, or cannot be resolved
     */
    public static void requireInside(Path directory, Path path) throws IOException {
        if (!physical(path).startsWith(physical(directory))) {
            throw leaves(path, directory);
        }
    }

    /** Resolves the links in a path whose last segments may not exist yet. */
    private static Path physical(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        for (Path segment : absolute) {
            current = current.resolve(segment);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) continue;
            try {
                current = current.toRealPath();
            } catch (IOException error) {
                throw new IOException("Cannot resolve " + current + ", possibly a broken symbolic link", error);
            }
        }
        return current;
    }

    /** Adds a root's real path. A root that no longer exists has no links to follow. */
    private static void addExistingDirectory(String root, Set<Path> roots) throws IOException {
        if (root == null || root.isBlank()) return;
        try {
            Path path = Path.of(root);
            if (Files.isDirectory(path)) roots.add(path.toRealPath());
        } catch (InvalidPathException error) {
            throw new IOException("Invalid indexed project root: " + root, error);
        }
    }

    private static IOException leaves(Path path, Path boundary) {
        return new IOException("Refusing " + path + ": a symbolic link leads out of " + boundary);
    }
}
