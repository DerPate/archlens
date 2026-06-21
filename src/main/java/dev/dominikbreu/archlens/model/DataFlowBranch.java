package dev.dominikbreu.archlens.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Branch point in a branch-aware data-flow topology.
 */
public class DataFlowBranch {
    /** Source language branch construct. */
    public enum Kind {
        IF,
        SWITCH,
        TERNARY,
        TRY
    }

    /** Stable branch identifier within the data-flow path. */
    public String id;
    /** Branch construct kind. */
    public Kind kind;
    /** Source location and extraction evidence. */
    public SourceInfo source;
    /** Arms leaving this branch. */
    public List<DataFlowBranchArm> arms = new ArrayList<>();

    /** Creates an empty branch for JSON deserialization. */
    public DataFlowBranch() {}

    /**
     * Creates a data-flow topology branch.
     *
     * @param id     stable branch identifier
     * @param kind   branch construct kind
     * @param source source location and extraction evidence
     * @param arms   arms leaving this branch
     */
    public DataFlowBranch(String id, Kind kind, SourceInfo source, List<DataFlowBranchArm> arms) {
        this.id = id;
        this.kind = kind;
        this.source = source;
        this.arms = new ArrayList<>(arms);
    }
}
