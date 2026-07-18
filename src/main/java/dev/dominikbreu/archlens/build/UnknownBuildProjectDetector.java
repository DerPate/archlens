package dev.dominikbreu.archlens.build;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Fallback {@link BuildProjectDetector} for source trees with no recognized build system. Treats the
 * directory as a single plain-Java module rooted at {@code src/main/java} (falling back to the root),
 * reported as {@link BuildSystem#UNKNOWN} with low confidence.
 */
public class UnknownBuildProjectDetector implements BuildProjectDetector {

    @Override
    public Optional<BuildProject> detect(File root) {
        if (root == null || !root.exists()) return Optional.empty();
        File sourceRoot = new File(root, "src/main/java");
        File resourceRoot = new File(root, "src/main/resources");
        BuildModule module = new BuildModule(
                root.getName(),
                root,
                null,
                "unknown",
                List.of(),
                sourceRoot.isDirectory() ? List.of(sourceRoot) : List.of(root),
                resourceRoot.isDirectory() ? List.of(resourceRoot) : List.of(),
                "plain-java-fallback");
        return Optional.of(new BuildProject(BuildSystem.UNKNOWN, root, List.of(module), "plain-java-fallback", 0.4));
    }
}
