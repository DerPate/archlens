package dev.dominikbreu.spoonmcp.build;

import java.io.File;
import java.util.List;

/** Detects build system metadata for a Java project root directory. */
public class BuildMetadataService {

    private final List<BuildProjectDetector> detectors;

    /** Creates a service with the default set of detectors (Gradle, Maven, fallback). */
    public BuildMetadataService() {
        this(List.of(
                new GradleBuildProjectDetector(), new MavenBuildProjectDetector(), new UnknownBuildProjectDetector()));
    }

    /**
     * Creates a service with a custom list of detectors tried in order.
     *
     * @param detectors the detectors to try, in priority order
     */
    public BuildMetadataService(List<BuildProjectDetector> detectors) {
        this.detectors = List.copyOf(detectors);
    }

    /**
     * Detects the build project at the given root directory.
     *
     * @param root the project root directory
     * @return the detected build project
     * @throws IllegalArgumentException if the root does not exist or cannot be read
     */
    public BuildProject detect(File root) {
        for (BuildProjectDetector detector : detectors) {
            var detected = detector.detect(root);
            if (detected.isPresent()) return detected.get();
        }
        throw new IllegalArgumentException("Project root does not exist or cannot be read: " + root);
    }
}
