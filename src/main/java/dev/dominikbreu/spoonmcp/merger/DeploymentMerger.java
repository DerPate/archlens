package dev.dominikbreu.spoonmcp.merger;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import java.io.File;
import java.util.List;

/**
 * Orchestrates Docker Compose and Ansible mergers across a list of project roots.
 */
public class DeploymentMerger {

    private final DockerComposeMerger composeMerger = new DockerComposeMerger();
    private final AnsibleMerger ansibleMerger = new AnsibleMerger();

    /** Creates a deployment merger with Docker Compose and Ansible support. */
    public DeploymentMerger() {}

    /**
     * Merges deployment metadata found near each project root into the architecture model.
     *
     * @param projectPaths project roots to inspect
     * @param model architecture model to update
     */
    public void merge(List<String> projectPaths, ArchitectureModel model) {
        for (String path : projectPaths) {
            File root = new File(path);
            composeMerger.merge(root, model);

            // check common Ansible directories
            for (String sub : List.of(".", "ansible", "deploy", "infra")) {
                File candidate;
                if (".".equals(sub)) {
                    candidate = root;
                } else {
                    candidate = new File(root, sub);
                }
                ansibleMerger.merge(candidate, model);
            }
        }
    }
}
