package dev.dominikbreu.spoonmcp.workflow;

import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import java.util.List;
import java.util.Map;

/**
 * Canonical workflow view over data-flow paths and typed continuation links.
 */
public record WorkflowGraph(
        List<DataFlowPath> rootPaths,
        Map<String, DataFlowPath> pathById,
        Map<String, Entrypoint> entrypointById,
        Map<String, List<WorkflowLink>> linksBySourcePathId) {

    public List<WorkflowLink> linksFrom(String pathId) {
        return linksBySourcePathId.getOrDefault(pathId, List.of());
    }

    public int totalLinks() {
        return linksBySourcePathId.values().stream().mapToInt(List::size).sum();
    }
}
