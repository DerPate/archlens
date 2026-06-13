package dev.dominikbreu.spoonmcp.build;

import java.io.File;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * Detected build project containing all modules discovered under a root directory.
 *
 * @param buildSystem the detected build system
 * @param root the project root directory
 * @param modules the modules discovered within the project
 * @param evidence description of how the project was detected
 * @param confidence the detection confidence score (0.0–1.0)
 */
public record BuildProject(
        BuildSystem buildSystem, File root, List<BuildModule> modules, String evidence, double confidence) {

    /** Applies defaults for null fields and defensively copies the module list. */
    public BuildProject {
        if (buildSystem == null) buildSystem = BuildSystem.UNKNOWN;
        modules = List.copyOf(modules == null ? List.of() : modules);
        if (StringUtils.isBlank(evidence)) evidence = "unknown";
    }
}
