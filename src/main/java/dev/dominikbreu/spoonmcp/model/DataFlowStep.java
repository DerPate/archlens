package dev.dominikbreu.spoonmcp.model;

/**
 * One hop in a data-flow path: a component method through which a tracked value passes.
 */
public class DataFlowStep {
    /** Zero-based position in the flow. */
    public int index;
    /** Component identifier. */
    public String componentId;
    /** Component display name. */
    public String componentName;
    /** Method name at this hop. */
    public String method;
    /** Name of the local variable or parameter holding the tracked value at this hop. */
    public String localName;

    /** Creates an empty step for JSON deserialization. */
    public DataFlowStep() {}

    public DataFlowStep(int index, String componentId, String componentName,
                        String method, String localName) {
        this.index         = index;
        this.componentId   = componentId;
        this.componentName = componentName;
        this.method        = method;
        this.localName     = localName;
    }
}
