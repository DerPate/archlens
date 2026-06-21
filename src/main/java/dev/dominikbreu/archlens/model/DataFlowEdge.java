package dev.dominikbreu.archlens.model;

/**
 * Directed edge between two nodes in a branch-aware data-flow topology.
 */
public class DataFlowEdge {
    /** Control-flow semantics for this edge. */
    public enum Kind {
        UNCONDITIONAL,
        CONDITIONAL,
        EXCEPTION
    }

    /** Source node id. */
    public String fromNodeId;
    /** Target node id. */
    public String toNodeId;
    /** Edge semantics. */
    public Kind kind;
    /** Branch id when this edge is guarded by a branch. */
    public String branchId;
    /** Branch arm id when this edge belongs to a specific arm. */
    public String branchArmId;
    /** Human-readable edge label. */
    public String label;

    /** Creates an empty edge for JSON deserialization. */
    public DataFlowEdge() {}

    /**
     * Creates a data-flow topology edge.
     *
     * @param fromNodeId  source node id
     * @param toNodeId    target node id
     * @param kind        edge semantics
     * @param branchId    branch id when guarded by a branch
     * @param branchArmId branch arm id for a specific arm
     * @param label       human-readable edge label
     */
    public DataFlowEdge(
            String fromNodeId, String toNodeId, Kind kind, String branchId, String branchArmId, String label) {
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.kind = kind;
        this.branchId = branchId;
        this.branchArmId = branchArmId;
        this.label = label;
    }
}
