package dev.dominikbreu.spoonmcp.merger;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.DeploymentEntry;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Merges Docker Compose service definitions into the architecture model as DeploymentEntry records.
 * Supports docker-compose.yml and docker-compose.yaml in any given directory.
 */
public class DockerComposeMerger {

    private final Yaml yaml = new Yaml();

    /** Creates a Docker Compose merger using the default YAML parser. */
    public DockerComposeMerger() {}

    /**
     * Parses a Docker Compose file in the project directory and appends deployment entries.
     *
     * @param projectDir project directory to inspect
     * @param model architecture model to update
     */
    public void merge(File projectDir, ArchitectureModel model) {
        File composeFile = resolveComposeFile(projectDir);
        if (composeFile == null) return;

        try (FileInputStream fis = new FileInputStream(composeFile)) {
            Object loaded = yaml.load(fis);
            if (!(loaded instanceof Map<?, ?> root)) return;
            Object servicesObj = root.get("services");
            if (!(servicesObj instanceof Map<?, ?> services)) return;

            for (Map.Entry<?, ?> entry : services.entrySet()) {
                model.deployments.add(
                        buildDeploymentEntry(String.valueOf(entry.getKey()), entry.getValue(), composeFile, model));
            }
        } catch (Exception _) {
        }
    }

    private DeploymentEntry buildDeploymentEntry(
            String serviceName, Object serviceObj, File composeFile, ArchitectureModel model) {
        DeploymentEntry de = new DeploymentEntry();
        de.id = "deploy:" + serviceName;
        de.name = serviceName;
        de.type = "docker-compose";
        de.source = composeFile.getAbsolutePath();
        if (serviceObj instanceof Map<?, ?> service) {
            addPorts(de, service.get("ports"));
            addDependsOn(de, service.get("depends_on"));
            linkAppIds(de, serviceName, model);
        }
        return de;
    }

    private void addPorts(DeploymentEntry de, Object portsObj) {
        if (portsObj instanceof List<?> ports) {
            for (Object p : ports) {
                de.ports.add(String.valueOf(p));
            }
        }
    }

    private void addDependsOn(DeploymentEntry de, Object depsObj) {
        switch (depsObj) {
            case List<?> deps -> deps.stream().map(String::valueOf).forEach(de.dependsOn::add);
            case Map<?, ?> deps -> deps.keySet().stream().map(String::valueOf).forEach(de.dependsOn::add);
            case null, default -> {}
        }
    }

    private void linkAppIds(DeploymentEntry de, String serviceName, ArchitectureModel model) {
        // link appIds by matching service name to known app names
        for (var app : model.applications) {
            if (app.name.equalsIgnoreCase(serviceName)
                    || serviceName.contains(app.name)
                    || app.name.contains(serviceName)) {
                de.appIds.add(app.id);
            }
        }
    }

    private File resolveComposeFile(File dir) {
        for (String name : List.of(
                "docker-compose.yml", "docker-compose.yaml",
                "compose.yml", "compose.yaml")) {
            File f = new File(dir, name);
            if (f.exists()) return f;
        }
        return null;
    }
}
