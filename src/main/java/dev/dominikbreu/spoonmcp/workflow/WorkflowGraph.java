package dev.dominikbreu.spoonmcp.workflow;

import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
import java.util.List;
import java.util.Map;

/**
 * Canonical workflow view over data-flow paths and typed continuation links.
 */
public record WorkflowGraph(
        List<DataFlowPath> rootPaths,
        Map<String, DataFlowPath> pathById,
        Map<EntrypointId, Entrypoint> entrypointById,
        Map<String, List<WorkflowLink>> linksBySourcePathId) {

    public WorkflowGraph {
        rootPaths = List.copyOf(rootPaths);
        pathById = Map.copyOf(pathById);
        entrypointById = Map.copyOf(entrypointById);
        linksBySourcePathId = linksBySourcePathId.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }

    public List<WorkflowLink> linksFrom(String pathId) {
        return linksBySourcePathId.getOrDefault(pathId, List.of());
    }

    public int totalLinks() {
        return linksBySourcePathId.values().stream().mapToInt(List::size).sum();
    }
}
