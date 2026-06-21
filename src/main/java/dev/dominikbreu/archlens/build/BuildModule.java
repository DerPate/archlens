package dev.dominikbreu.archlens.build;

import java.io.File;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * Detected metadata for a single build module (Maven module or Gradle subproject).
 *
 * @param name the module artifact name
 * @param root the module root directory
 * @param parentName the parent module name, or {@code null} for root modules
 * @param packagingType the packaging type (e.g. {@code "jar"}, {@code "pom"})
 * @param plugins the build plugin ids detected in this module
 * @param sourceRoots the source directories for this module
 * @param resourceRoots the resource directories for this module
 * @param evidence description of how the module was detected
 */
public record BuildModule(
        String name,
        File root,
        String parentName,
        String packagingType,
        List<String> plugins,
        List<File> sourceRoots,
        List<File> resourceRoots,
        String evidence) {

    /** Defensively copies list fields and normalises blank strings to {@code "unknown"}. */
    public BuildModule {
        plugins = List.copyOf(plugins == null ? List.of() : plugins);
        sourceRoots = List.copyOf(sourceRoots == null ? List.of() : sourceRoots);
        resourceRoots = List.copyOf(resourceRoots == null ? List.of() : resourceRoots);
        if (StringUtils.isBlank(packagingType)) packagingType = "unknown";
        if (StringUtils.isBlank(evidence)) evidence = "unknown";
    }
}
