package dev.dominikbreu.spoonmcp.workflow;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.ids.FieldRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        Map<String, Entrypoint> entrypointById = indexEntrypointsById(model);
        LinkIndex index = new LinkIndex(
                indexPathsById(model), entrypointById, collectDirectOwnerWrittenFields(model, entrypointById));

        List<WorkflowLink> links = new ArrayList<>();
        for (DataFlowPath fromPath : model.dataFlowPaths) {
            Entrypoint fromEntrypoint = entrypointFor(fromPath, index.entrypointById());
            if (!policy.isWorkflowRoot(fromEntrypoint)) {
                continue;
            }
            linkFromPath(fromPath, fromEntrypoint, index, links);
        }
        return links;
    }

    /** Lookup tables shared across the entire link pass. */
    private record LinkIndex(
            Map<String, DataFlowPath> pathById,
            Map<String, Entrypoint> entrypointById,
            Set<FieldRef> directOwnerWrittenFields) {}

    private static Map<String, DataFlowPath> indexPathsById(ArchitectureModel model) {
        Map<String, DataFlowPath> pathById = new HashMap<>();
        for (DataFlowPath path : model.dataFlowPaths) {
            pathById.put(path.id, path);
        }
        return pathById;
    }

    private static Map<String, Entrypoint> indexEntrypointsById(ArchitectureModel model) {
        Map<String, Entrypoint> entrypointById = new HashMap<>();
        for (Entrypoint entrypoint : model.entrypoints) {
            entrypointById.put(entrypoint.id.serialize(), entrypoint);
        }
        return entrypointById;
    }

    private static Entrypoint entrypointFor(DataFlowPath path, Map<String, Entrypoint> entrypointById) {
        return path.entrypointId != null ? entrypointById.get(path.entrypointId.serialize()) : null;
    }

    /**
     * Pre-computes store fields that have at least one same-component (direct-owner) writer.
     * Cross-component writes to these fields are shadowed side-effects, not pipeline triggers.
     */
    private static Set<FieldRef> collectDirectOwnerWrittenFields(
            ArchitectureModel model, Map<String, Entrypoint> entrypointById) {
        Set<FieldRef> directOwnerWrittenFields = new HashSet<>();
        for (DataFlowPath p : model.dataFlowPaths) {
            Entrypoint ep = entrypointFor(p, entrypointById);
            if (ep == null || ep.componentId == null) continue;
            for (DataFlowSink s : p.sinks) {
                if (s.kind == DataFlowSink.Kind.STORE
                        && s.fieldOwnerComponentId != null
                        && ep.componentId.equals(s.fieldOwnerComponentId)) {
                    directOwnerWrittenFields.add(new FieldRef(s.fieldOwnerComponentId, s.fieldName));
                }
            }
        }
        return directOwnerWrittenFields;
    }

    private void linkFromPath(
            DataFlowPath fromPath, Entrypoint fromEntrypoint, LinkIndex index, List<WorkflowLink> links) {
        for (DataFlowSink sink : fromPath.sinks) {
            if (sink.linkedPathIds == null || sink.linkedPathIds.isEmpty()) {
                continue;
            }
            WorkflowLink.Kind kind = kindFor(sink);
            if (kind == null) {
                continue;
            }
            for (String targetPathId : sink.linkedPathIds) {
                WorkflowLink link = tryBuildLink(fromPath, fromEntrypoint, sink, kind, targetPathId, index);
                if (link != null) {
                    links.add(link);
                }
            }
        }
    }

    private WorkflowLink tryBuildLink(
            DataFlowPath fromPath,
            Entrypoint fromEntrypoint,
            DataFlowSink sink,
            WorkflowLink.Kind kind,
            String targetPathId,
            LinkIndex index) {
        DataFlowPath toPath = index.pathById().get(targetPathId);
        if (toPath == null) {
            return null;
        }
        Entrypoint toEntrypoint = entrypointFor(toPath, index.entrypointById());
        if (!policy.isWorkflowRoot(toEntrypoint)) {
            return null;
        }
        // A cross-component write to a field that also has a direct same-component writer is a
        // shadowed side-effect, not a pipeline trigger.
        if (policy.isShadowedCrossComponentStoreWrite(sink, fromEntrypoint, index.directOwnerWrittenFields())) {
            return null;
        }
        // A scheduler pre-loading reference data into a store that a primary consumer reads is a
        // background data-feed, not a pipeline trigger. Skip the link so the consumer remains an
        // independent pipeline root.
        if (policy.isBackgroundDataFeedLink(fromEntrypoint, toEntrypoint, kind)) {
            return null;
        }
        return new WorkflowLink(
                kind,
                fromPath.id,
                toPath.id,
                fromPath.entrypointId != null ? fromPath.entrypointId.serialize() : null,
                toPath.entrypointId != null ? toPath.entrypointId.serialize() : null,
                sink.channel,
                sink.fieldOwnerComponentId != null ? sink.fieldOwnerComponentId.serialize() : null,
                sink.fieldName,
                sink.entityType,
                sink.repositoryOperation,
                sink.linkEvidence,
                confidenceFor(kind));
    }

    private static WorkflowLink.Kind kindFor(DataFlowSink sink) {
        if (sink.kind == DataFlowSink.Kind.MESSAGING) return WorkflowLink.Kind.MESSAGING;
        if (sink.kind == DataFlowSink.Kind.EVENT_BUS) return WorkflowLink.Kind.EVENT_BUS;
        if (sink.kind == DataFlowSink.Kind.STORE) return WorkflowLink.Kind.STATE_HANDOFF;
        if (sink.kind == DataFlowSink.Kind.PERSISTENCE && sink.linkEvidence != null)
            return WorkflowLink.Kind.PERSISTENCE_HANDOFF;
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
