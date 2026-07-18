package dev.dominikbreu.archlens.model;

import dev.dominikbreu.archlens.model.ids.ComponentId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Directed method-call edge between two architecture components, extracted from
 * actual {@code CtInvocation} nodes in the source rather than injection annotations.
 */
public class CallEdge {
    /** Source-level control-flow context for the call site. */
    public enum ControlFlowKind {
        /** Call site not guarded by any branch. */
        UNCONDITIONAL,
        /** Call site in the then branch of an if statement. */
        IF_THEN,
        /** Call site in the else branch of an if statement. */
        IF_ELSE,
        /** Call site in a case branch of a switch statement. */
        SWITCH_CASE,
        /** Call site in the default branch of a switch statement. */
        SWITCH_DEFAULT,
        /** Call site in the then expression of a ternary operator. */
        TERNARY_THEN,
        /** Call site in the else expression of a ternary operator. */
        TERNARY_ELSE,
        /** Call site in a catch block. */
        CATCH,
        /** Call site in a finally block. */
        FINALLY
    }

    /** Stable edge identifier: {@code call:fromCompId#fromMethod->toCompId#toMethod}. */
    public String id;
    /** Calling component identifier. */
    public ComponentId fromComponentId;
    /** Simple name of the method that contains the call site. */
    public String fromMethod;
    /** Called component identifier. */
    public ComponentId toComponentId;
    /** Simple name of the method being invoked. */
    public String toMethod;
    /** Call kind: {@code direct}, {@code event-bus}, or {@code messaging}. */
    public String callKind;
    /** Source evidence for the call site. */
    public SourceInfo source;
    /** Source-level control-flow context of the call site. */
    public ControlFlowKind controlFlowKind = ControlFlowKind.UNCONDITIONAL;
    /** Stable id of the enclosing branch group, or null for unconditional calls. */
    public String branchGroupId;
    /** Stable id of the branch arm, or null for unconditional calls. */
    public String branchArmId;
    /** Human-readable branch arm label. */
    public String branchLabel;
    /** Source location of the branch construct that owns this call site. */
    public SourceInfo controlSource;
    /** Evidence kind used to resolve the invocation receiver, e.g. constructor-assignment. */
    public String receiverEvidence;
    /**
     * Simple local or parameter name at the root of the receiver expression when visible,
     * e.g. {@code order} for {@code order.getId()}.
     */
    public String receiverLocalName;
    /** Confidence of receiver resolution, between 0 and 1. */
    public double receiverConfidence;
    /** True when this edge is weak evidence and should not be used by default workflow traversal. */
    public boolean ambiguous;
    /** True when polymorphic expansion was capped for this receiver. */
    public boolean receiverExpansionCapped;
    /**
     * Caller-parameter-name → callee-parameter-name mapping for arguments that are
     * simple variable reads. Only populated when the callee method is in the same Spoon model.
     */
    public Map<String, String> paramMapping = new LinkedHashMap<>();
    /**
     * Callee-parameter-name → resolved constant string value for arguments that are
     * string literals or resolvable static-final field reads at the call site.
     * Used by the data-flow tracer to substitute parameter-named topics in outbound sinks.
     */
    public Map<String, String> resolvedLiteralArgs = new LinkedHashMap<>();
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
