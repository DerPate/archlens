package dev.dominikbreu.spoonmcp.model;

/**
 * Terminal point in a data-flow path where a tracked value leaves the component or is persisted.
 */
public class DataFlowSink {
    /** Sink category: {@code persistence}, {@code messaging}, {@code http-outbound}, {@code event-bus}. */
    public String kind;
    /** Component that acts as the sink. */
    public String componentId;
    /** Display name of the sink component. */
    public String componentName;
    /** Method name at which the value is consumed or forwarded. */
    public String method;
    /** Source location of the call site that reaches this sink. */
    public SourceInfo source;

    /** Creates an empty sink for JSON deserialization. */
    public DataFlowSink() {}

    public DataFlowSink(String kind, String componentId, String componentName,
                        String method, SourceInfo source) {
        this.kind          = kind;
        this.componentId   = componentId;
        this.componentName = componentName;
        this.method        = method;
        this.source        = source;
    }
}
