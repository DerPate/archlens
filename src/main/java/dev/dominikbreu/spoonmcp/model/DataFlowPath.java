package dev.dominikbreu.spoonmcp.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Inter-procedural data-flow path from an entrypoint parameter to one or more sinks.
 *
 * <p>When {@link #sinks} is empty the parameter is traced through the call chain but
 * does not reach any classified sink — it may flow into unanalyzed code or be dropped.
 */
public class DataFlowPath {
    /** Stable identifier: {@code df:<entrypointId>#<trackedParam>}. */
    public String id;
    /** Entrypoint from which this path originates. */
    public String entrypointId;
    /** Name of the entrypoint method parameter being tracked. */
    public String trackedParam;
    /** Ordered hops through which the value passes. */
    public List<DataFlowStep> steps = new ArrayList<>();
    /** Classified endpoints where the tracked value is persisted, published, or forwarded. */
    public List<DataFlowSink> sinks = new ArrayList<>();

    /** Creates an empty path for JSON deserialization. */
    public DataFlowPath() {}
}
