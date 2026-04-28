package dev.dominikbreu.spoonmcp.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Directed method-call edge between two architecture components, extracted from
 * actual {@code CtInvocation} nodes in the source rather than injection annotations.
 */
public class CallEdge {
    /** Stable edge identifier: {@code call:fromCompId#fromMethod->toCompId#toMethod}. */
    public String id;
    /** Calling component identifier. */
    public String fromComponentId;
    /** Simple name of the method that contains the call site. */
    public String fromMethod;
    /** Called component identifier. */
    public String toComponentId;
    /** Simple name of the method being invoked. */
    public String toMethod;
    /** Call kind: {@code direct}, {@code event-bus}, or {@code messaging}. */
    public String callKind;
    /** Source evidence for the call site. */
    public SourceInfo source;
    /**
     * Caller-parameter-name → callee-parameter-name mapping for arguments that are
     * simple variable reads. Only populated when the callee method is in the same Spoon model.
     */
    public Map<String, String> paramMapping = new LinkedHashMap<>();

    /** Creates an empty call edge for JSON deserialization. */
    public CallEdge() {}
}
