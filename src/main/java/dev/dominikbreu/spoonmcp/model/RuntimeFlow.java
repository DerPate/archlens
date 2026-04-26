package dev.dominikbreu.spoonmcp.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered runtime path inferred from an entrypoint through dependency relationships.
 */
public class RuntimeFlow {
    /** Stable runtime flow identifier. */
    public String id;
    /** Entrypoint identifier that starts this flow. */
    public String entrypointId;
    /** Ordered steps in the inferred runtime flow. */
    public List<RuntimeFlowStep> steps = new ArrayList<>();

    /** Creates an empty runtime flow for JSON deserialization. */
    public RuntimeFlow() {}
}
