package dev.dominikbreu.spoonmcp.build;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public class MavenBuildProjectDetector implements BuildProjectDetector {

    @Override
    public Optional<BuildProject> detect(File root) {
        Model model = readModel(root).orElse(null);
        if (model == null) return Optional.empty();

        List<BuildModule> modules = new ArrayList<>();
        collectLeafModules(root, null, modules);
        if (modules.isEmpty()) {
            modules.add(toModule(root, null, model));
        }
        return Optional.of(new BuildProject(BuildSystem.MAVEN, root, modules, "pom.xml", 0.95));
    }

    public List<String> readModules(File root) {
        return readModel(root).map(model -> List.copyOf(model.getModules())).orElseGet(List::of);
    }

    public String readPackaging(File root) {
        return readModel(root)
                .map(Model::getPackaging)
                .filter(value -> value != null && !value.isBlank())
                .orElse("jar");
    }

    private void collectLeafModules(File root, String parentName, List<BuildModule> out) {
        Model model = readModel(root).orElse(null);
        if (model == null) return;
        List<String> children = model.getModules();
        if (children == null || children.isEmpty()) {
            out.add(toModule(root, parentName, model));
            return;
        }
        String thisName = root.getName();
        for (String child : children) {
            collectLeafModules(new File(root, child), thisName, out);
        }
    }

    private BuildModule toModule(File root, String parentName, Model model) {
        Set<String> plugins = new LinkedHashSet<>();
        if (model.getBuild() != null && model.getBuild().getPlugins() != null) {
            for (Plugin plugin : model.getBuild().getPlugins()) {
                if (plugin.getArtifactId() != null) plugins.add(plugin.getArtifactId());
                if (plugin.getGroupId() != null && plugin.getArtifactId() != null) {
                    plugins.add(plugin.getGroupId() + ":" + plugin.getArtifactId());
                }
            }
        }
        File sourceRoot = new File(root, "src/main/java");
        File resourceRoot = new File(root, "src/main/resources");
        return new BuildModule(
                root.getName(),
                root,
                parentName,
                model.getPackaging() == null || model.getPackaging().isBlank() ? "jar" : model.getPackaging(),
                List.copyOf(plugins),
                sourceRoot.isDirectory() ? List.of(sourceRoot) : List.of(),
                resourceRoot.isDirectory() ? List.of(resourceRoot) : List.of(),
                "pom.xml");
    }

    private Optional<Model> readModel(File root) {
        File pom = new File(root, "pom.xml");
        if (!pom.isFile()) return Optional.empty();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(pom), StandardCharsets.UTF_8)) {
            return Optional.of(new MavenXpp3Reader().read(reader));
        } catch (java.io.IOException | XmlPullParserException ignored) {
            return Optional.empty();
        }
    }
}
