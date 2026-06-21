package dev.dominikbreu.archlens.model;

import dev.dominikbreu.archlens.model.ids.ComponentId;

/**
 * Node in a branch-aware data-flow topology.
 */
public class DataFlowNode {
    /** Role of this node in the topology. */
    public enum Kind {
        ROOT,
        METHOD,
        SINK
    }

    /** Stable node identifier within the data-flow path. */
    public String id;
    /** Node role. */
    public Kind kind;
    /** Component identifier when the node belongs to a known component. */
    public ComponentId componentId;
    /** Component display name. */
    public String componentName;
    /** Method represented by this node. */
    public String method;
    /** Local variable or parameter name represented by this node. */
    public String localName;
    /** Source location and extraction evidence. */
    public SourceInfo source;

    /** Creates an empty node for JSON deserialization. */
    public DataFlowNode() {}

    /**
     * Creates a data-flow topology node.
     *
     * @param id            stable node identifier
     * @param kind          node role
     * @param componentId   component identifier
     * @param componentName component display name
     * @param method        method represented by this node
     * @param localName     local variable or parameter name
     * @param source        source location and extraction evidence
     */
    public DataFlowNode(
            String id,
            Kind kind,
            ComponentId componentId,
            String componentName,
            String method,
            String localName,
            SourceInfo source) {
        this.id = id;
        this.kind = kind;
        this.componentId = componentId;
        this.componentName = componentName;
        this.method = method;
        this.localName = localName;
        this.source = source;
    }
}
