package dev.dominikbreu.spoonmcp.merger;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.DeploymentEntry;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

/**
 * Merges Ansible inventory and playbook definitions into the architecture model.
 * Supports INI-style inventory files and YAML playbooks.
 */
public class AnsibleMerger {

    private static final String HOSTS = "hosts";

    private final Yaml yaml = new Yaml();

    /** Creates an Ansible merger using the default YAML parser. */
    public AnsibleMerger() {}

    /**
     * Parses supported Ansible inventory and playbook files in a directory.
     *
     * @param ansibleDir directory to inspect
     * @param model architecture model to update
     */
    public void merge(File ansibleDir, ArchitectureModel model) {
        if (!ansibleDir.exists() || !ansibleDir.isDirectory()) return;
        mergeInventory(ansibleDir, model);
        mergePlaybooks(ansibleDir, model);
    }

    // ── inventory ────────────────────────────────────────────────────────────

    private void mergeInventory(File dir, ArchitectureModel model) {
        for (String name : List.of("inventory", HOSTS, "inventory.ini", "hosts.ini")) {
            File f = new File(dir, name);
            if (f.exists()) {
                parseIniInventory(f, model);
                return;
            }
        }
        // Try inventory/ subdirectory
        File inventoryDir = new File(dir, "inventory");
        if (inventoryDir.isDirectory()) {
            File[] files = inventoryDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().endsWith(".ini") || HOSTS.equals(f.getName())) {
                        parseIniInventory(f, model);
                    }
                }
            }
        }
    }

    private void parseIniInventory(File f, ArchitectureModel model) {
        try {
            List<String> lines = Files.readAllLines(f.toPath());
            String currentGroup = null;
            Map<String, DeploymentEntry> groups = new LinkedHashMap<>();

            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.charAt(0) == '#' || line.startsWith(";")) continue;

                if (line.startsWith("[") && line.endsWith("]")) {
                    String header = line.substring(1, line.length() - 1);
                    if (header.endsWith(":vars") || header.endsWith(":children")) continue;
                    currentGroup = header;
                    groups.computeIfAbsent(currentGroup, g -> {
                        DeploymentEntry de = new DeploymentEntry();
                        de.id = "deploy:ansible:" + g;
                        de.name = g;
                        de.type = "ansible-group";
                        de.source = f.getAbsolutePath();
                        return de;
                    });
                } else if (currentGroup != null) {
                    String host = line.split("\\s+")[0];
                    groups.get(currentGroup).hosts.add(host);
                }
            }

            model.deployments.addAll(groups.values());
        } catch (Exception _) {
        }
    }

    // ── playbooks ────────────────────────────────────────────────────────────

    private void mergePlaybooks(File dir, ArchitectureModel model) {
        File[] yamls = dir.listFiles(
                f -> f.isFile() && (f.getName().endsWith(".yml") || f.getName().endsWith(".yaml")));
        if (yamls == null) return;
        for (File f : yamls) {
            parsePlaybook(f, model);
        }
    }

    private void parsePlaybook(File f, ArchitectureModel model) {
        try (FileInputStream fis = new FileInputStream(f)) {
            Object doc = yaml.load(fis);
            if (!(doc instanceof List<?> plays)) return;
            for (Object playObj : plays) {
                if (!(playObj instanceof Map<?, ?> play)) continue;
                DeploymentEntry de = buildPlayDeployment(play, f);
                if (de != null) model.deployments.add(de);
            }
        } catch (Exception _) {
        }
    }

    private DeploymentEntry buildPlayDeployment(Map<?, ?> play, File f) {
        Object hostsObj = play.get(HOSTS);
        String hosts = String.valueOf(hostsObj != null ? hostsObj : "");
        Object rolesObj = play.get("roles");
        if (rolesObj == null) return null;

        DeploymentEntry de = new DeploymentEntry();
        de.id = "deploy:ansible:play:" + f.getName() + ":" + hosts;
        de.name = f.getName().replace(".yml", "").replace(".yaml", "");
        de.type = "ansible-host";
        de.source = f.getAbsolutePath();
        de.hosts.add(hosts);

        if (rolesObj instanceof List<?> roles) {
            for (Object roleEntry : roles) {
                de.roles.add(roleName(roleEntry));
            }
        }
        return de;
    }

    private String roleName(Object roleEntry) {
        return switch (roleEntry) {
            case Map<?, ?> roleMap -> {
                Object role = roleMap.get("role");
                yield String.valueOf(role != null ? role : roleEntry);
            }
            default -> String.valueOf(roleEntry);
        };
    }
}
