package dev.dominikbreu.spoonmcp.workflow;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the canonical workflow graph consumed by pipeline and graph projections.
 */
public final class WorkflowGraphBuilder {

    private final WorkflowTraversalPolicy policy;
    private final WorkflowLinker linker;

    public WorkflowGraphBuilder() {
        this(new WorkflowTraversalPolicy());
    }

    public WorkflowGraphBuilder(WorkflowTraversalPolicy policy) {
        this.policy = policy;
        this.linker = new WorkflowLinker(policy);
    }

    public WorkflowGraph build(ArchitectureModel model) {
        if (model == null) {
            return new WorkflowGraph(List.of(), Map.of(), Map.of(), Map.of());
        }

        Map<String, Entrypoint> entrypointById = new LinkedHashMap<>();
        for (Entrypoint entrypoint : model.entrypoints) {
            entrypointById.put(entrypoint.id, entrypoint);
        }

        Map<String, DataFlowPath> pathById = new LinkedHashMap<>();
        for (DataFlowPath path : model.dataFlowPaths) {
            Entrypoint entrypoint = entrypointById.get(path.entrypointId);
            if (policy.isWorkflowRoot(entrypoint)) {
                pathById.put(path.id, path);
            }
        }

        List<WorkflowLink> links = linker.link(model).stream()
                .filter(link -> pathById.containsKey(link.fromPathId()))
                .filter(link -> pathById.containsKey(link.toPathId()))
                .toList();

        Map<String, List<WorkflowLink>> linksBySource = new HashMap<>();
        Set<String> hasIncoming = new LinkedHashSet<>();
        for (WorkflowLink link : links) {
            linksBySource
                    .computeIfAbsent(link.fromPathId(), ignored -> new ArrayList<>())
                    .add(link);
            hasIncoming.add(link.toPathId());
        }

        List<DataFlowPath> roots = pathById.values().stream()
                .filter(path -> !hasIncoming.contains(path.id))
                .filter(path -> !linksBySource.getOrDefault(path.id, List.of()).isEmpty())
                .toList();

        return new WorkflowGraph(roots, pathById, entrypointById, linksBySource);
    }
}
