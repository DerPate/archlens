package dev.dominikbreu.spoonmcp.model;

import dev.dominikbreu.spoonmcp.model.ids.ComponentId;

/**
 * One hop in a data-flow path: a component method through which a tracked value passes.
 */
public class DataFlowStep {
    /** Zero-based position in the flow. */
    public int index;
    /** Component identifier. */
    public ComponentId componentId;
    /** Component display name. */
    public String componentName;
    /** Method name at this hop. */
    public String method;
    /** Name of the local variable or parameter holding the tracked value at this hop. */
    public String localName;

    /** Creates an empty step for JSON deserialization. */
    public DataFlowStep() {}

    /**
     * Creates a step.
     *
     * @param index         zero-based position in the flow
     * @param componentId   component identifier
     * @param componentName component display name
     * @param method        method name at this hop
     * @param localName     name of the local variable holding the tracked value
     */
    public DataFlowStep(int index, ComponentId componentId, String componentName, String method, String localName) {
        this.index = index;
        this.componentId = componentId;
        this.componentName = componentName;
        this.method = method;
        this.localName = localName;
    }
}
