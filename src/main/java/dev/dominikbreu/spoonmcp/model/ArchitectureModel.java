package dev.dominikbreu.spoonmcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Root architecture document produced by indexing one or more Java project roots.
 */
public class ArchitectureModel {
    /** Workspace path or path list summary used as the extraction source. */
    public String workspacePath;
    /** Time at which the model was produced. */
    public Instant analysedAt;
    /** Discovered applications, Maven modules, and deployment units. */
    public List<AppEntry> applications = new ArrayList<>();
    /** Discovered source-level architecture components. */
    public List<Component> components = new ArrayList<>();
    /** Runtime entrypoints such as REST endpoints, EJB methods, consumers, and schedulers. */
    public List<Entrypoint> entrypoints = new ArrayList<>();
    /** External or exposed interfaces represented independently from entrypoints. */
    public List<InterfaceEntry> interfaces = new ArrayList<>();
    /** Directed component dependencies extracted from source evidence. */
    public List<Dependency> dependencies = new ArrayList<>();
    /** Persisted runtime flow paths inferred from entrypoints and dependency evidence. */
    @JsonProperty("runtime_flows")
    public List<RuntimeFlow> runtimeFlows = new ArrayList<>();
    /** Logical containers inferred from component roles and packages. */
    public List<Container> containers = new ArrayList<>();
    /** Deployment metadata merged from Docker Compose, Ansible, or similar descriptors. */
    public List<DeploymentEntry> deployments = new ArrayList<>();

    /** Creates an empty architecture model for JSON deserialization. */
    public ArchitectureModel() {}

    /**
     * Creates a model for the supplied workspace path and sets {@link #analysedAt}.
     *
     * @param workspacePath indexed workspace path or path summary
     */
    public ArchitectureModel(String workspacePath) {
        this.workspacePath = workspacePath;
        this.analysedAt = Instant.now();
    }
}
