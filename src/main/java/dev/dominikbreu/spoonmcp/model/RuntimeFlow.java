package dev.dominikbreu.spoonmcp.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered runtime path inferred from an entrypoint through dependency relationships.
 */
public class RuntimeFlow {
    /** Stable runtime flow identifier. */
    public String id;
    /** Entrypoint identifier that starts this flow. */
    public String entrypointId;
    /** Ordered steps in the inferred runtime flow. */
    public List<RuntimeFlowStep> steps = new ArrayList<>();
    /** Directed edges between component steps, preserving branching topology. */
    public List<FlowEdge> edges = new ArrayList<>();

    /** Creates an empty runtime flow for JSON deserialization. */
    public RuntimeFlow() {}

    /** A directed call edge between two component steps in the flow. */
    public static class FlowEdge {
        /** Source component identifier. */
        public String fromId;
        /** Target component identifier. */
        public String toId;
        /** Optional edge label (e.g. method name or channel). */
        public String label;

        /** Creates an empty edge for JSON deserialization. */
        public FlowEdge() {}

        /**
         * Creates an edge.
         *
         * @param fromId source component id
         * @param toId   target component id
         * @param label  optional label; may be null
         */
        public FlowEdge(String fromId, String toId, String label) {
            this.fromId = fromId;
            this.toId = toId;
            this.label = label;
        }
    }
}
