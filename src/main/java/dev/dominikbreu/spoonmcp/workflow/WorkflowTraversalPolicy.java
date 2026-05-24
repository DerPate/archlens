package dev.dominikbreu.spoonmcp.workflow;

import dev.dominikbreu.spoonmcp.model.CallEdge;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import java.util.Locale;
import java.util.Set;

/**
 * Shared workflow traversal rules for data-flow, pipelines, runtime flows, and use cases.
 */
public final class WorkflowTraversalPolicy {

    private static final Set<String> LIFECYCLE_KEYWORDS =
            Set.of("shutdown", "stop", "destroy", "close", "halt", "predestroy", "cleanup");

    private static final Set<ComponentType> HIDDEN_TYPES = Set.of(ComponentType.UTILITY);

    /** Returns true when an entrypoint should be considered a workflow root or segment. */
    public boolean isWorkflowRoot(Entrypoint entrypoint) {
        return entrypoint != null && !isLifecycleEntrypoint(entrypoint);
    }

    /** Detects framework lifecycle CDI observers that should not become user-facing workflow paths. */
    public boolean isLifecycleEntrypoint(Entrypoint entrypoint) {
        if (entrypoint == null || entrypoint.type != EntrypointType.CDI_EVENT_OBSERVER) {
            return false;
        }
        String name = lower(entrypoint.name);
        String path = lower(entrypoint.path);
        return LIFECYCLE_KEYWORDS.stream().anyMatch(name::contains)
                || LIFECYCLE_KEYWORDS.stream().anyMatch(path::contains);
    }

    /** Returns true for calls that cross asynchronous segment boundaries. */
    public boolean isAsyncBoundary(CallEdge edge) {
        if (edge == null || edge.callKind == null) {
            return false;
        }
        return "messaging".equals(edge.callKind) || "event-bus".equals(edge.callKind);
    }

    /** Returns true when a call can be traversed as an in-process continuation. */
    public boolean canTraverseInline(CallEdge edge) {
        return edge != null && !isAsyncBoundary(edge) && !edge.receiverExpansionCapped;
    }

    /** Returns true when a component should appear in human-facing workflow diagrams. */
    public boolean isHumanVisible(Component component) {
        if (component == null || component.type == null) {
            return false;
        }
        if (HIDDEN_TYPES.contains(component.type)) {
            return false;
        }
        return !isRawMessagingClient(component);
    }

    /** Detects transport clients represented as raw HTTP clients with messaging stereotypes. */
    public boolean isRawMessagingClient(Component component) {
        return component != null
                && component.type == ComponentType.HTTP_CLIENT
                && component.stereotypes != null
                && component.stereotypes.contains("messaging");
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
