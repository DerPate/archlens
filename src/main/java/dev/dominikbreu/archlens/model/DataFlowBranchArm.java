package dev.dominikbreu.archlens.model;

/**
 * One arm of a branch in a branch-aware data-flow topology.
 */
public class DataFlowBranchArm {
    /** Stable arm identifier within the data-flow path. */
    public String id;
    /** Owning branch id. */
    public String branchId;
    /** Human-readable arm label. */
    public String label;
    /** First node id entered by this arm. */
    public String entryNodeId;

    /** Creates an empty branch arm for JSON deserialization. */
    public DataFlowBranchArm() {}

    /**
     * Creates a branch arm.
     *
     * @param id          stable arm identifier
     * @param branchId    owning branch id
     * @param label       human-readable arm label
     * @param entryNodeId first node entered by this arm
     */
    public DataFlowBranchArm(String id, String branchId, String label, String entryNodeId) {
        this.id = id;
        this.branchId = branchId;
        this.label = label;
        this.entryNodeId = entryNodeId;
    }
}
