package dev.dominikbreu.archlens.workflow;

import dev.dominikbreu.archlens.model.DataFlowPath;
import dev.dominikbreu.archlens.model.Entrypoint;
import dev.dominikbreu.archlens.model.ids.EntrypointId;
import java.util.List;
import java.util.Map;

/**
 * Canonical workflow view over data-flow paths and typed continuation links.
 *
 * @param rootPaths data-flow paths not targeted by any workflow link (chain entry points)
 * @param pathById all data-flow paths indexed by their string id
 * @param entrypointById all entrypoints indexed by their id
 * @param linksBySourcePathId outgoing workflow links indexed by source path id
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

    /**
     * Returns all outgoing workflow links for the given path id.
     *
     * @param pathId the source data-flow path id
     * @return the outgoing links, or an empty list if none
     */
    public List<WorkflowLink> linksFrom(String pathId) {
        return linksBySourcePathId.getOrDefault(pathId, List.of());
    }

    /**
     * Returns the total number of workflow links across all paths.
     *
     * @return the sum of all link list sizes
     */
    public int totalLinks() {
        return linksBySourcePathId.values().stream().mapToInt(List::size).sum();
    }
}
