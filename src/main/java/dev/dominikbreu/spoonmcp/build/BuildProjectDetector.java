package dev.dominikbreu.spoonmcp.build;

import java.io.File;
import java.util.Optional;

public interface BuildProjectDetector {
    Optional<BuildProject> detect(File root);
}
