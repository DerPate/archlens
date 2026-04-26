package dev.dominikbreu.spoonmcp.scanner;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import spoon.Launcher;
import spoon.reflect.CtModel;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(21);
        launcher.getEnvironment().setShouldCompile(false);

        for (String path : projectPaths) {
            addSourceRoots(launcher, new File(path));
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
     * Returns the list of Maven submodule directory names declared in &lt;modules&gt;,
     * or an empty list if none are found.
     *
     * @param projectRoot Maven project root
     * @return declared Maven module directory names
     */
    public List<String> readMavenModules(File projectRoot) {
        return readMavenModel(projectRoot)
            .<List<String>>map(model -> new ArrayList<>(model.getModules()))
            .orElseGet(List::of);
    }

    /**
     * Reads the Maven packaging type from a project root.
     *
     * @param projectRoot Maven project root
     * @return packaging type, defaulting to jar when no POM value exists
     */
    public String readPackagingType(File projectRoot) {
        return readMavenModel(projectRoot)
            .map(Model::getPackaging)
            .filter(p -> p != null && !p.isBlank())
            .orElse("jar");
    }

    private Optional<Model> readMavenModel(File projectRoot) {
        File pom = new File(projectRoot, "pom.xml");
        if (!pom.exists()) return Optional.empty();
        try (FileReader reader = new FileReader(pom)) {
            return Optional.of(new MavenXpp3Reader().read(reader));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
