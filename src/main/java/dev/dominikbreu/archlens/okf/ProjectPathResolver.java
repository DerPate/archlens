package dev.dominikbreu.archlens.okf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/** Resolves OKF output paths while confining them to an indexed project root. */
public final class ProjectPathResolver {
    /**
     * Resolves the indexed project and its project-local output paths.
     *
     * @param indexedRootValues indexed application root paths
     * @param requestedProject requested project root, or {@code null} when it can be inferred
     * @param bundleValue project-relative output bundle path, or {@code null} for the default
     * @param templateValue project-relative template file path, or {@code null}
     * @return the selected project and its contained output paths
     * @throws IOException if a supplied path cannot be resolved on the file system
     */
    public ResolvedPaths resolve(
            Collection<String> indexedRootValues,
            String requestedProject,
            String bundleValue,
            String templateValue)
            throws IOException {
        List<Path> roots =
                indexedRootValues.stream()
                        .map(Path::of)
                        .map(ProjectPathResolver::realDirectory)
                        .distinct()
                        .toList();
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("Indexed graph has no project roots");
        }

        Path project = selectProject(roots, requestedProject);
        Path bundle =
                contained(
                        project,
                        bundleValue == null
                                ? Path.of("docs", "agent-wiki")
                                : relative(bundleValue, "bundlePath"),
                        false);
        Path template =
                templateValue == null
                        ? null
                        : contained(project, relative(templateValue, "templatePath"), true);
        return new ResolvedPaths(project, bundle, template);
    }

    private static Path realDirectory(Path path) {
        try {
            Path realPath = path.toAbsolutePath().normalize().toRealPath();
            if (!Files.isDirectory(realPath)) {
                throw new IllegalArgumentException("Indexed project root must be a directory: " + path);
            }
            return realPath;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot resolve indexed project root: " + path, exception);
        }
    }

    private static Path selectProject(List<Path> roots, String requestedProject) {
        if (requestedProject == null) {
            if (roots.size() == 1) {
                return roots.getFirst();
            }
            throw new IllegalArgumentException("projectPath is required when multiple project roots are indexed");
        }

        Path requested = realDirectory(Path.of(requestedProject));
        if (!roots.contains(requested)) {
            throw new IllegalArgumentException("projectPath must match an indexed project root");
        }
        return requested;
    }

    private static Path relative(String value, String name) {
        Path path = Path.of(value);
        if (path.isAbsolute()) {
            throw new IllegalArgumentException(name + " must be project-relative");
        }
        return path;
    }

    private static Path contained(Path project, Path relative, boolean mustExist) throws IOException {
        Path candidate = project.resolve(relative).normalize();
        if (!candidate.startsWith(project)) {
            throw new IllegalArgumentException("Path is outside indexed project");
        }

        verifySymlinkContainment(project, candidate);
        Path existingParent = nearestExistingParent(candidate);
        if (!existingParent.toRealPath().startsWith(project)) {
            throw new IllegalArgumentException("Path is outside indexed project");
        }

        if (mustExist) {
            if (!Files.isRegularFile(candidate)) {
                throw new IllegalArgumentException("templatePath must be an existing regular file");
            }
            if (!candidate.toRealPath().startsWith(project)) {
                throw new IllegalArgumentException("templatePath is outside indexed project");
            }
        }
        return candidate;
    }

    private static void verifySymlinkContainment(Path project, Path candidate) throws IOException {
        Path current = project;
        for (Path segment : project.relativize(candidate)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current) && !current.toRealPath().startsWith(project)) {
                throw new IllegalArgumentException("Path traverses a symlink outside indexed project");
            }
        }
    }

    private static Path nearestExistingParent(Path path) {
        Path current = path;
        while (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            current = current.getParent();
        }
        return current;
    }

    /**
     * Resolved paths confined to one indexed project.
     *
     * @param projectPath selected indexed project root
     * @param bundlePath project-local documentation bundle path
     * @param templatePath project-local custom template path, or {@code null}
     */
    public record ResolvedPaths(Path projectPath, Path bundlePath, Path templatePath) {}
}
