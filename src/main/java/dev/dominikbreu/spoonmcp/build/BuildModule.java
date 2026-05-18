package dev.dominikbreu.spoonmcp.build;

import java.io.File;
import java.util.List;

public record BuildModule(
        String name,
        File root,
        String parentName,
        String packagingType,
        List<String> plugins,
        List<File> sourceRoots,
        List<File> resourceRoots,
        String evidence) {

    public BuildModule {
        plugins = List.copyOf(plugins == null ? List.of() : plugins);
        sourceRoots = List.copyOf(sourceRoots == null ? List.of() : sourceRoots);
        resourceRoots = List.copyOf(resourceRoots == null ? List.of() : resourceRoots);
        if (packagingType == null || packagingType.isBlank()) packagingType = "unknown";
        if (evidence == null || evidence.isBlank()) evidence = "unknown";
    }
}
