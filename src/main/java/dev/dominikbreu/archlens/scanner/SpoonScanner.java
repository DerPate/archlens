package dev.dominikbreu.archlens.scanner;

import dev.dominikbreu.archlens.build.BuildMetadataService;
import dev.dominikbreu.archlens.build.BuildModule;
import dev.dominikbreu.archlens.build.MavenBuildProjectDetector;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.CtModel;

/**
 * Builds Spoon models for Maven or plain Java project roots.
 */
public class SpoonScanner {

    /** Creates a scanner with default Spoon configuration. */
    public SpoonScanner() {}

    /**
     * Scans all supplied project roots into a single Spoon model.
     *
     * @param projectPaths project root directories
     * @return Spoon model containing all discovered source types
     */
    public CtModel scan(List<String> projectPaths) {
        List<BuildModule> modules = new ArrayList<>();
        BuildMetadataService metadataService = new BuildMetadataService();
        for (String path : projectPaths) {
            modules.addAll(metadataService.detect(new File(path)).modules());
        }
        return scanModules(modules);
    }

    /**
     * Scans normalized build modules into a single Spoon model.
     *
     * @param modules the build modules to scan
     * @return the Spoon model built from all module source roots
     */
    public CtModel scanModules(List<BuildModule> modules) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(21);
        launcher.getEnvironment().setShouldCompile(false);

        for (BuildModule module : modules) {
            addSourceRoots(launcher, module);
        }

        launcher.buildModel();
        return launcher.getModel();
    }

    /**
     * Adds source roots for a project directory. For multi-module Maven projects
     * (pom.xml with &lt;modules&gt;) each module's src/main/java is added recursively.
     * For single-module projects, src/main/java (or the path itself as fallback) is added.
     *
     * @param launcher Spoon launcher to configure
     * @param root project or module root directory
     */
    public void addSourceRoots(Launcher launcher, File root) {
        if (!root.exists()) return;

        List<String> modules = readMavenModules(root);
        if (!modules.isEmpty()) {
            for (String module : modules) {
                File moduleDir = new File(root, module);
                addSourceRoots(launcher, moduleDir); // recurse
            }
            return;
        }

        // Single module: prefer src/main/java
        File srcMain = new File(root, "src/main/java");
        if (srcMain.exists()) {
            launcher.addInputResource(srcMain.getAbsolutePath());
        } else {
            launcher.addInputResource(root.getAbsolutePath());
        }
    }

    /**
     * Adds all source roots declared for a normalized module to the Spoon launcher.
     *
     * @param launcher the Spoon launcher to configure
     * @param module the build module whose source roots should be added
     */
    public void addSourceRoots(Launcher launcher, BuildModule module) {
        if (module == null) return;
        if (module.sourceRoots().isEmpty()) {
            launcher.addInputResource(module.root().getAbsolutePath());
            return;
        }
        for (File sourceRoot : module.sourceRoots()) {
            if (sourceRoot.exists()) launcher.addInputResource(sourceRoot.getAbsolutePath());
        }
    }

    /**
     * Returns the list of Maven submodule directory names declared in &lt;modules&gt;,
     * or an empty list if none are found.
     *
     * @param projectRoot Maven project root
     * @return declared Maven module directory names
     */
    public List<String> readMavenModules(File projectRoot) {
        return new MavenBuildProjectDetector().readModules(projectRoot);
    }

    /**
     * Reads the Maven packaging type from a project root.
     *
     * @param projectRoot Maven project root
     * @return packaging type, defaulting to jar when no POM value exists
     */
    public String readPackagingType(File projectRoot) {
        return new MavenBuildProjectDetector().readPackaging(projectRoot);
    }
}
