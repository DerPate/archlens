package dev.dominikbreu.spoonmcp.workflow;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns serialized data-flow sink links into typed workflow continuation edges.
 */
public final class WorkflowLinker {

    private final WorkflowTraversalPolicy policy;

    public WorkflowLinker() {
        this(new WorkflowTraversalPolicy());
    }

    public WorkflowLinker(WorkflowTraversalPolicy policy) {
        this.policy = policy;
    }

    public List<WorkflowLink> link(ArchitectureModel model) {
        if (model == null) {
            return List.of();
        }

        Map<String, DataFlowPath> pathById = new HashMap<>();
        for (DataFlowPath path : model.dataFlowPaths) {
            pathById.put(path.id, path);
        }

        Map<String, Entrypoint> entrypointById = new HashMap<>();
        for (Entrypoint entrypoint : model.entrypoints) {
            entrypointById.put(entrypoint.id, entrypoint);
        }

        List<WorkflowLink> links = new ArrayList<>();
        for (DataFlowPath fromPath : model.dataFlowPaths) {
            Entrypoint fromEntrypoint = entrypointById.get(fromPath.entrypointId);
            if (!policy.isWorkflowRoot(fromEntrypoint)) {
                continue;
            }
            for (DataFlowSink sink : fromPath.sinks) {
                if (sink.linkedPathIds == null || sink.linkedPathIds.isEmpty()) {
                    continue;
                }
                WorkflowLink.Kind kind = kindFor(sink);
                if (kind == null) {
                    continue;
                }
                for (String targetPathId : sink.linkedPathIds) {
                    DataFlowPath toPath = pathById.get(targetPathId);
                    if (toPath == null) {
                        continue;
                    }
                    Entrypoint toEntrypoint = entrypointById.get(toPath.entrypointId);
                    if (!policy.isWorkflowRoot(toEntrypoint)) {
                        continue;
                    }
                    links.add(new WorkflowLink(
                            kind,
                            fromPath.id,
                            toPath.id,
                            fromPath.entrypointId,
                            toPath.entrypointId,
                            sink.channel,
                            sink.fieldOwnerComponentId,
                            sink.fieldName,
                            sink.entityType,
                            sink.repositoryOperation,
                            sink.linkEvidence,
                            confidenceFor(kind)));
                }
            }
        }
        return links;
    }

    private static WorkflowLink.Kind kindFor(DataFlowSink sink) {
        if (sink.kind == DataFlowSink.Kind.MESSAGING) return WorkflowLink.Kind.MESSAGING;
        if (sink.kind == DataFlowSink.Kind.EVENT_BUS) return WorkflowLink.Kind.EVENT_BUS;
        if (sink.kind == DataFlowSink.Kind.STORE) return WorkflowLink.Kind.STATE_HANDOFF;
        if (sink.kind == DataFlowSink.Kind.PERSISTENCE && sink.linkEvidence != null) return WorkflowLink.Kind.PERSISTENCE_HANDOFF;
        return null;
    }

    private static double confidenceFor(WorkflowLink.Kind kind) {
        return switch (kind) {
            case MESSAGING, EVENT_BUS -> 0.90;
            case STATE_HANDOFF -> 0.75;
            case PERSISTENCE_HANDOFF -> 0.60;
        };
    }
}
