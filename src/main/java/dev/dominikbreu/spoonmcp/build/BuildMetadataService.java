package dev.dominikbreu.spoonmcp.build;

import java.io.File;
import java.util.List;

public class BuildMetadataService {

    private final List<BuildProjectDetector> detectors;

    public BuildMetadataService() {
        this(List.of(
                new GradleBuildProjectDetector(),
                new MavenBuildProjectDetector(),
                new UnknownBuildProjectDetector()));
    }

    public BuildMetadataService(List<BuildProjectDetector> detectors) {
        this.detectors = List.copyOf(detectors);
    }

    public BuildProject detect(File root) {
        for (BuildProjectDetector detector : detectors) {
            var detected = detector.detect(root);
            if (detected.isPresent()) return detected.get();
        }
        throw new IllegalArgumentException("Project root does not exist or cannot be read: " + root);
    }
}
