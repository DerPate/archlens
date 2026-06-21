package dev.dominikbreu.archlens.model;

import dev.dominikbreu.archlens.model.ids.DataFlowPathId;
import dev.dominikbreu.archlens.model.ids.EntrypointId;
import java.util.ArrayList;
import java.util.List;

/**
 * Inter-procedural data-flow path from an entrypoint parameter to one or more sinks.
 *
 * <p>When {@link #sinks} is empty the parameter is traced through the call chain but
 * does not reach any classified sink — it may flow into unanalyzed code or be dropped.
 */
public class DataFlowPath {
    /** Stable identifier derived from the entrypoint id and tracked parameter. */
    public DataFlowPathId id;
    /** Entrypoint from which this path originates. */
    public EntrypointId entrypointId;
    /** Name of the entrypoint method parameter being tracked. */
    public String trackedParam;
    /** Ordered hops through which the value passes. */
    public List<DataFlowStep> steps = new ArrayList<>();
    /** Branch-aware topology nodes for this path. */
    public List<DataFlowNode> flowNodes = new ArrayList<>();
    /** Branch-aware topology edges for this path. */
    public List<DataFlowEdge> flowEdges = new ArrayList<>();
    /** Branch points observed while tracing this path. */
    public List<DataFlowBranch> branches = new ArrayList<>();
    /** Classified endpoints where the tracked value is persisted, published, or forwarded. */
    public List<DataFlowSink> sinks = new ArrayList<>();

    /** Creates an empty path for JSON deserialization. */
    public DataFlowPath() {}
}
