package dev.dominikbreu.spoonmcp.build;

import java.io.File;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public record BuildProject(
        BuildSystem buildSystem, File root, List<BuildModule> modules, String evidence, double confidence) {

    public BuildProject {
        if (buildSystem == null) buildSystem = BuildSystem.UNKNOWN;
        modules = List.copyOf(modules == null ? List.of() : modules);
        if (StringUtils.isBlank(evidence)) evidence = "unknown";
    }
}
