package dev.dominikbreu.spoonmcp.build;

import java.io.File;
import java.util.Optional;

/** Strategy interface for detecting build project metadata from a project root directory. */
public interface BuildProjectDetector {
    /**
     * Detects build project metadata at the given root directory.
     *
     * @param root the project root directory
     * @return the detected project, or empty if this detector does not recognise the project
     */
    Optional<BuildProject> detect(File root);
}
