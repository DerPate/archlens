package dev.dominikbreu.spoonmcp.model;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
    /**
     * Callee parameter names whose mapping was synthesised by descending into a non-trivial
     * argument expression (constructor calls, nested invocations, ternaries, casts) rather
     * than read directly as a top-level {@code CtVariableRead}. Tracer can downgrade
     * confidence on paths that traversed these.
     */
    public Set<String> syntheticParamMappings = new LinkedHashSet<>();
    /**
     * For invocations whose result is bound to a local variable
     * (e.g. {@code Order o = repo.find(id)}), the simple name of that variable.
     * Combined with {@link #returnsTracked} this lets the tracer propagate tracking
     * from a callee's return value to the caller's LHS.
     */
    public String assignedToVar;
    /**
     * True when the callee method has at least one return statement whose expression
     * derives from one of its parameters. Heuristic — see {@code CallGraphExtractor}.
     */
    public boolean returnsTracked;
    /**
     * Caller-method locals that were reassigned (via another invocation result)
     * before reaching this call site. Tracer treats a tracked name appearing here
     * as "stop tracking" — the value at this point is no longer the original.
     */
    public Set<String> killedTrackedNames = new LinkedHashSet<>();

    /** Creates an empty call edge for JSON deserialization. */
    public CallEdge() {}
}
