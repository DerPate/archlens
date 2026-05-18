package dev.dominikbreu.spoonmcp.build;

import java.io.File;
import java.util.List;

public record BuildProject(
        BuildSystem buildSystem,
        File root,
        List<BuildModule> modules,
        String evidence,
        double confidence) {

    public BuildProject {
        if (buildSystem == null) buildSystem = BuildSystem.UNKNOWN;
        modules = List.copyOf(modules == null ? List.of() : modules);
        if (evidence == null || evidence.isBlank()) evidence = "unknown";
    }
}
