package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.GraphQuery.DataFlowBranchArmNode;
import dev.dominikbreu.archlens.cache.GraphQuery.DataFlowBranchNode;
import dev.dominikbreu.archlens.cache.GraphQuery.DataFlowNodeNode;
import dev.dominikbreu.archlens.cache.GraphQuery.DataFlowPathNode;
import dev.dominikbreu.archlens.cache.GraphQuery.DataFlowSinkNode;
import dev.dominikbreu.archlens.cache.GraphQuery.DataFlowStepNode;
import dev.dominikbreu.archlens.cache.GraphQuery.EntrypointNode;
import dev.dominikbreu.archlens.cache.GraphQuery.GraphEdge;
import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.extractor.RuntimeFlowInferrer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool that exposes pre-computed data-flow paths from entrypoint parameters to sinks.
 */
public class TraceDataFlowTool {

    private final ModelCache cache;

    /**
     * Creates the tool with access to the model cache.
     *
     * @param cache model cache to query
     */
    public TraceDataFlowTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Traces where an entrypoint parameter or message payload flows, returning the data-flow paths
     * and their sinks as text and structured data.
     *
     * @param args arguments identifying the entrypoint and tracked parameter
     * @return the traced data-flow paths
     */
    public ToolResult execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (!graph.isIndexed()) return ToolResult.error("No workspace indexed yet. Call index_workspace first.");

            if (!graph.hasCallGraph()) {
                return ToolResult.error(
                        "No call-graph data available. Re-index the workspace to enable data-flow tracing.");
            }

            List<DataFlowPathNode> paths = graph.allDataFlowPaths();
            paths = filterByEntrypointId(paths, ToolArgs.getString(args, "entrypointId"));
            paths = filterByEntrypointName(paths, ToolArgs.getString(args, "entrypointName"), graph);
            paths = filterByParam(paths, ToolArgs.getString(args, "param"));
            paths = filterBySinkKind(paths, ToolArgs.getString(args, "sinkKind"), graph);

            if (paths.isEmpty()) {
                return ToolResult.success("No data-flow paths found for the given filters.", List.of());
            }

            return new ToolResult(format(paths, graph), structured(paths, graph));
        } catch (Exception e) {
            return ToolResult.error("Error tracing data flow: " + e.getMessage());
        }
    }

    /**
     * Builds the structured (machine-readable) representation of the traced paths, one entry per
     * path with its entrypoint label, tracked parameter, and non-null sink fields (including
     * evidence, when present).
     *
     * @param paths data-flow paths to serialize
     * @param graph graph used to resolve entrypoints and sinks
     * @return one map per path, each sink map pruned of null-valued fields
     */
    private List<Map<String, Object>> structured(List<DataFlowPathNode> paths, GraphQuery graph) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DataFlowPathNode path : paths) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", path.id().serialize());
            entry.put(
                    "entrypointId",
                    path.entrypointId() != null ? path.entrypointId().serialize() : null);
            entry.put("entrypoint", entrypointLabel(path, graph));
            entry.put("trackedParam", path.trackedParam());
            entry.put(
                    "sinks",
                    graph.pathSinks(path.id()).stream()
                            .map(sink -> {
                                Map<String, Object> sinkMap = new LinkedHashMap<>();
                                sinkMap.put(
                                        "kind",
                                        sink.sinkKind() != null
                                                ? sink.sinkKind().value()
                                                : null);
                                sinkMap.put("name", sink.name());
                                sinkMap.put("method", sink.method());
                                sinkMap.put("fieldName", sink.fieldName());
                                sinkMap.put("entityType", sink.entityType());
                                sinkMap.put("repositoryOperation", sink.repositoryOperation());
                                sinkMap.put("channel", sink.channel());
                                sinkMap.put(
                                        "broker",
                                        sink.broker() != null ? sink.broker().name() : null);
                                sinkMap.put("topic", sink.topic());
                                Map<String, Object> evidence = ToolArgs.evidenceAsMap(sink.properties());
                                if (!evidence.isEmpty()) sinkMap.put("evidence", evidence);
                                sinkMap.entrySet().removeIf(item -> item.getValue() == null);
                                return sinkMap;
                            })
                            .toList());
            result.add(entry);
        }
        return result;
    }

    /**
     * Keeps only paths whose entrypoint ID equals or contains the given filter. A {@code null}
     * filter passes all paths through unchanged.
     *
     * @param paths data-flow paths to filter
     * @param epFilter entrypoint ID (or substring) to match, or {@code null} to skip filtering
     * @return the paths whose entrypoint ID matches, or {@code paths} unchanged if no filter given
     */
    private static List<DataFlowPathNode> filterByEntrypointId(List<DataFlowPathNode> paths, String epFilter) {
        if (epFilter == null) return paths;
        return paths.stream()
                .filter(p -> p.entrypointId() != null
                        && (p.entrypointId().serialize().equals(epFilter)
                                || p.entrypointId().serialize().contains(epFilter)))
                .toList();
    }

    /**
     * Keeps only paths whose entrypoint matches the given reference, which may combine an HTTP
     * method and a path/name fragment (e.g. {@code "GET /users"}). The method and path portions
     * are split via {@link RuntimeFlowInferrer}, and the path portion is matched case-insensitively
     * against the entrypoint name or via prefix against its route path. A {@code null} filter
     * passes all paths through unchanged.
     *
     * @param paths data-flow paths to filter
     * @param nameFilter entrypoint reference to match, or {@code null} to skip filtering
     * @param graph graph used to resolve entrypoints
     * @return the paths whose entrypoint matches, or {@code paths} unchanged if no filter given
     */
    private static List<DataFlowPathNode> filterByEntrypointName(
            List<DataFlowPathNode> paths, String nameFilter, GraphQuery graph) {
        if (nameFilter == null) return paths;
        String methodFilter = RuntimeFlowInferrer.extractMethodFromRef(nameFilter);
        String pathFilter = RuntimeFlowInferrer.extractPathFromRef(nameFilter);
        String lower = pathFilter.toLowerCase();
        return paths.stream()
                .filter(p -> entrypointNameMatches(p, graph, methodFilter, pathFilter, lower))
                .toList();
    }

    /**
     * Checks whether a path's entrypoint matches the given method and name/path filters. Returns
     * {@code false} when the path has no entrypoint or the entrypoint cannot be resolved, or when
     * an HTTP method filter is given and does not match (case-insensitively). Otherwise a match
     * requires either the entrypoint name to contain {@code lower} or its route path to share the
     * given prefix.
     *
     * @param p path whose entrypoint is checked
     * @param graph graph used to resolve the entrypoint
     * @param methodFilter HTTP method to require, or {@code null} to accept any method
     * @param pathFilter route path prefix to match against the entrypoint's path
     * @param lower lower-cased name fragment to match against the entrypoint's name
     * @return {@code true} if the path's entrypoint matches all given filters
     */
    private static boolean entrypointNameMatches(
            DataFlowPathNode p, GraphQuery graph, String methodFilter, String pathFilter, String lower) {
        if (p.entrypointId() == null) return false;
        EntrypointNode ep = graph.entrypoint(p.entrypointId()) instanceof EntrypointNode en ? en : null;
        if (ep == null) return false;
        if (methodFilter != null && !methodFilter.equalsIgnoreCase(ep.httpMethod())) return false;
        return ep.name().toLowerCase().contains(lower) || RuntimeFlowInferrer.pathPrefixMatches(ep.path(), pathFilter);
    }

    /**
     * Keeps only paths whose tracked parameter equals or contains the given filter. A {@code null}
     * filter passes all paths through unchanged.
     *
     * @param paths data-flow paths to filter
     * @param paramFilter tracked-parameter name (or substring) to match, or {@code null} to skip
     *     filtering
     * @return the paths whose tracked parameter matches, or {@code paths} unchanged if no filter
     *     given
     */
    private static List<DataFlowPathNode> filterByParam(List<DataFlowPathNode> paths, String paramFilter) {
        if (paramFilter == null) return paths;
        return paths.stream()
                .filter(p -> p.trackedParam() != null
                        && (p.trackedParam().equals(paramFilter)
                                || p.trackedParam().contains(paramFilter)))
                .toList();
    }

    /**
     * Keeps only paths that have at least one sink whose kind equals the given filter
     * (case-insensitively). A {@code null} filter passes all paths through unchanged.
     *
     * @param paths data-flow paths to filter
     * @param sinkFilter sink kind to require, or {@code null} to skip filtering
     * @param graph graph used to resolve each path's sinks
     * @return the paths with a matching sink, or {@code paths} unchanged if no filter given
     */
    private static List<DataFlowPathNode> filterBySinkKind(
            List<DataFlowPathNode> paths, String sinkFilter, GraphQuery graph) {
        if (sinkFilter == null) return paths;
        String lower = sinkFilter.toLowerCase();
        return paths.stream()
                .filter(p -> graph.pathSinks(p.id()).stream()
                        .anyMatch(s ->
                                s.sinkKind() != null && s.sinkKind().value().equalsIgnoreCase(lower)))
                .toList();
    }

    /**
     * Renders the human-readable text report: a leading path count followed by one formatted
     * section per path.
     *
     * @param paths data-flow paths to render
     * @param graph graph used to resolve entrypoints, topology, and sinks
     * @return the rendered report text
     */
    private String format(List<DataFlowPathNode> paths, GraphQuery graph) {
        StringBuilder sb = new StringBuilder();
        sb.append(paths.size()).append(" data-flow path(s):\n\n");
        for (DataFlowPathNode path : paths) {
            formatPath(sb, path, graph);
        }
        return sb.toString();
    }

    /**
     * Appends one path's section to the report: header line with entrypoint and tracked parameter,
     * path ID, its flow (rendered as a step list when no flow-node topology is recorded, otherwise
     * as the full topology graph), and its sinks.
     *
     * @param sb buffer to append the rendered section to
     * @param path data-flow path being rendered
     * @param graph graph used to resolve entrypoint, topology, and sinks
     */
    private void formatPath(StringBuilder sb, DataFlowPathNode path, GraphQuery graph) {
        sb.append("## ")
                .append(entrypointLabel(path, graph))
                .append(" → param: ")
                .append(path.trackedParam())
                .append("\n");
        sb.append("  id: ").append(path.id().serialize()).append("\n");

        List<DataFlowNodeNode> flowNodes = graph.pathFlowNodes(path.id());
        if (flowNodes.isEmpty()) {
            formatSteps(sb, path, graph);
        } else {
            formatTopology(sb, path, flowNodes, graph);
        }
        formatSinks(sb, path, graph);
        sb.append("\n");
    }

    /**
     * Builds a display label for a path's entrypoint from two independent choices: an
     * {@code "<HTTP method> "} prefix is prepended when the entrypoint has an HTTP method, and the
     * entrypoint's path is used when present, else its name — regardless of whether the method
     * prefix was added. Falls back to the raw entrypoint ID when the entrypoint cannot be resolved,
     * and to an empty string when the path has no entrypoint at all.
     *
     * @param path data-flow path whose entrypoint is labeled
     * @param graph graph used to resolve the entrypoint
     * @return the entrypoint label, or an empty string if the path has no entrypoint
     */
    private static String entrypointLabel(DataFlowPathNode path, GraphQuery graph) {
        if (path.entrypointId() == null) return "";
        EntrypointNode ep = graph.entrypoint(path.entrypointId()) instanceof EntrypointNode en ? en : null;
        if (ep != null) {
            return (ep.httpMethod() != null ? ep.httpMethod() + " " : "") + (ep.path() != null ? ep.path() : ep.name());
        }
        return path.entrypointId().serialize();
    }

    /**
     * Appends the legacy flat step list for a path (used when no flow-node topology was recorded).
     * Appends nothing when the path has no data-flow steps; otherwise appends one numbered line per
     * step showing the component, method, and local variable name.
     *
     * @param sb buffer to append the rendered steps to
     * @param path data-flow path whose steps are rendered
     * @param graph graph used to resolve the path's steps
     */
    private static void formatSteps(StringBuilder sb, DataFlowPathNode path, GraphQuery graph) {
        List<DataFlowStepNode> steps = graph.pathDataFlowSteps(path.id());
        if (steps.isEmpty()) return;
        sb.append("  flow:\n");
        for (DataFlowStepNode step : steps) {
            sb.append("    ")
                    .append(step.stepIndex() + 1)
                    .append(". ")
                    .append(step.componentName())
                    .append(".")
                    .append(step.method())
                    .append(" (as '")
                    .append(step.localName())
                    .append("')\n");
        }
    }

    /**
     * Appends the full flow-graph rendering for a path: one {@code "N<i>"}-aliased line per flow
     * node, followed by branches and topology edges. Flow nodes are aliased both by their original
     * flow-node ID (for branch-arm lookups) and by their graph vertex ID (for edge lookups), both
     * assigned in list order as {@code N0}, {@code N1}, etc.; branches are aliased the same way as
     * {@code B0}, {@code B1}, etc.
     *
     * @param sb buffer to append the rendered topology to
     * @param path data-flow path whose topology is rendered
     * @param flowNodes ordered flow nodes to alias and render
     * @param graph graph used to resolve branches and edges
     */
    private void formatTopology(
            StringBuilder sb, DataFlowPathNode path, List<DataFlowNodeNode> flowNodes, GraphQuery graph) {
        // alias by original flowNodeId (e.g. "n0") for branch-arm lookups, AND by graph vertex ID for edge lookups
        Map<String, String> flowIdAlias = new LinkedHashMap<>(); // flowNodeId → "N0"
        Map<String, String> vertexIdAlias = new LinkedHashMap<>(); // graphVertexId → "N0"
        for (int i = 0; i < flowNodes.size(); i++) {
            String alias = "N" + i;
            flowIdAlias.put(flowNodes.get(i).flowNodeId(), alias);
            vertexIdAlias.put(flowNodes.get(i).id().value(), alias);
        }
        List<DataFlowBranchNode> branches = graph.pathBranches(path.id());
        Map<String, String> branchAlias = new LinkedHashMap<>();
        for (int i = 0; i < branches.size(); i++) {
            branchAlias.put(branches.get(i).branchId(), "B" + i);
        }
        sb.append("  flow graph:\n");
        for (DataFlowNodeNode node : flowNodes) {
            sb.append("    ")
                    .append(flowIdAlias.get(node.flowNodeId()))
                    .append(" ")
                    .append(nodeLabel(node))
                    .append(" [")
                    .append(node.nodeKind() != null ? node.nodeKind() : "node")
                    .append("]\n");
        }
        formatBranches(sb, branches, branchAlias, flowIdAlias, graph);
        formatTopologyEdges(sb, path, vertexIdAlias, branchAlias, graph);
    }

    /**
     * Builds a display label for a flow node: {@code "<component>.<method>"} when both are present,
     * falling back to whichever of the two is non-blank, or an empty string if neither is set.
     *
     * @param node flow node to label
     * @return the node label
     */
    private static String nodeLabel(DataFlowNodeNode node) {
        String component = node.componentName() != null ? node.componentName() : "";
        String method = node.method() != null ? node.method() : "";
        if (!component.isBlank() && !method.isBlank()) return component + "." + method;
        if (!component.isBlank()) return component;
        return method;
    }

    /**
     * Appends the branches section of the topology rendering. Appends nothing when there are no
     * branches; otherwise appends one line per branch (alias, upper-cased branch kind, and source
     * location, when known) followed by one indented line per branch arm showing the arm label and
     * the alias of the node it enters. Aliases fall back to the raw ID when not found in the given
     * maps.
     *
     * @param sb buffer to append the rendered branches to
     * @param branches path branches to render
     * @param branchAlias branch ID to display-alias map
     * @param nodeAlias flow-node ID to display-alias map, used for arm entry points
     * @param graph graph used to resolve each branch's arms
     */
    private static void formatBranches(
            StringBuilder sb,
            List<DataFlowBranchNode> branches,
            Map<String, String> branchAlias,
            Map<String, String> nodeAlias,
            GraphQuery graph) {
        if (branches.isEmpty()) return;
        sb.append("  branches:\n");
        for (DataFlowBranchNode branch : branches) {
            sb.append("    ")
                    .append(branchAlias.getOrDefault(branch.branchId(), branch.branchId()))
                    .append(" ")
                    .append(branch.branchKind() != null ? branch.branchKind().toUpperCase() : "")
                    .append(sourceLabel(branch))
                    .append("\n");
            for (DataFlowBranchArmNode arm : graph.branchArms(branch.id())) {
                sb.append("      ")
                        .append(arm.armLabel())
                        .append(" -> ")
                        .append(nodeAlias.getOrDefault(arm.entryNodeId(), arm.entryNodeId()))
                        .append("\n");
            }
        }
    }

    /**
     * Appends the edges section of the topology rendering. Appends nothing when the path has no
     * flow edges; otherwise appends one line per edge as {@code "<from> -> <to> [<label>]"}, using
     * vertex aliases (falling back to the raw vertex ID when not found), where the label is the
     * edge's {@code "label"} property when non-blank, else its {@code "edgeKind"} property. When
     * the edge carries a {@code "branchId"} property, the branch alias (and arm ID, when present)
     * is appended in parentheses.
     *
     * @param sb buffer to append the rendered edges to
     * @param path data-flow path whose edges are rendered
     * @param vertexIdAlias graph vertex ID to display-alias map
     * @param branchAlias branch ID to display-alias map
     * @param graph graph used to resolve the path's flow edges
     */
    private static void formatTopologyEdges(
            StringBuilder sb,
            DataFlowPathNode path,
            Map<String, String> vertexIdAlias,
            Map<String, String> branchAlias,
            GraphQuery graph) {
        List<GraphEdge> edges = graph.pathFlowEdges(path.id());
        if (edges.isEmpty()) return;
        sb.append("  edges:\n");
        for (GraphEdge edge : edges) {
            Map<String, Object> p = edge.properties();
            String fromAlias = vertexIdAlias.getOrDefault(
                    edge.fromId().value(), edge.fromId().value());
            String toAlias =
                    vertexIdAlias.getOrDefault(edge.toId().value(), edge.toId().value());
            Object labelVal = p.get("label");
            String label = (labelVal != null && !String.valueOf(labelVal).isBlank())
                    ? String.valueOf(labelVal)
                    : String.valueOf(p.getOrDefault("edgeKind", ""));
            sb.append("    ")
                    .append(fromAlias)
                    .append(" -> ")
                    .append(toAlias)
                    .append(" [")
                    .append(label)
                    .append("]");
            Object branchIdObj = p.get("branchId");
            Object branchArmIdObj = p.get("branchArmId");
            if (branchIdObj != null) {
                String branchId = String.valueOf(branchIdObj);
                sb.append(" (").append(branchAlias.getOrDefault(branchId, branchId));
                if (branchArmIdObj != null) sb.append("/").append(branchArmIdObj);
                sb.append(")");
            }
            sb.append("\n");
        }
    }

    /**
     * Builds the trailing {@code " <file>:<line>"} source-location suffix for a branch, using only
     * the file's base name. Returns an empty string when the branch's {@code "sourceFile"} property
     * is missing or {@code "unknown"}.
     *
     * @param branch branch whose source location is labeled
     * @return the source-location suffix, or an empty string if unknown
     */
    private static String sourceLabel(DataFlowBranchNode branch) {
        Map<String, Object> props = branch.properties();
        Object file = props.get("sourceFile");
        if (file == null || "unknown".equals(file)) return "";
        String f = String.valueOf(file);
        int slash = f.lastIndexOf('/');
        return " " + (slash >= 0 ? f.substring(slash + 1) : f) + ":" + props.get("sourceLine");
    }

    /**
     * Appends the sinks section for a path. Appends nothing when the path has no sinks; otherwise
     * appends one bullet line per sink showing its kind (or {@code "?"} when unknown), name, and
     * method, plus the field name for {@code "store"} sinks that have one, the evidence confidence
     * band when present, and the sink's source location (see {@link #appendSinkSource}).
     *
     * @param sb buffer to append the rendered sinks to
     * @param path data-flow path whose sinks are rendered
     * @param graph graph used to resolve the path's sinks
     */
    private static void formatSinks(StringBuilder sb, DataFlowPathNode path, GraphQuery graph) {
        List<DataFlowSinkNode> sinks = graph.pathSinks(path.id());
        if (sinks.isEmpty()) return;
        sb.append("  sinks:\n");
        for (DataFlowSinkNode sink : sinks) {
            sb.append("    - [")
                    .append(sink.sinkKind() != null ? sink.sinkKind().value() : "?")
                    .append("] ")
                    .append(sink.name())
                    .append(".")
                    .append(sink.method());
            if ("store".equals(sink.sinkKind() != null ? sink.sinkKind().value() : "") && sink.fieldName() != null) {
                sb.append("  field=").append(sink.fieldName());
            }
            Object confidenceBand = sink.properties().get("confidenceBand");
            if (confidenceBand != null) sb.append("  evidence=").append(confidenceBand);
            appendSinkSource(sb, sink);
            sb.append("\n");
        }
    }

    /**
     * Appends the trailing {@code " (<file>:<line>)"} source-location suffix for a sink, using only
     * the file's base name. Appends nothing when the sink has no source, or its file is missing or
     * {@code "unknown"}.
     *
     * @param sb buffer to append the source-location suffix to
     * @param sink sink whose source location is appended
     */
    private static void appendSinkSource(StringBuilder sb, DataFlowSinkNode sink) {
        var source = sink.source();
        if (source == null || source.file == null || "unknown".equals(source.file)) return;
        String f = source.file;
        int slash = f.lastIndexOf('/');
        sb.append("  (")
                .append(slash >= 0 ? f.substring(slash + 1) : f)
                .append(":")
                .append(source.line)
                .append(")");
    }
}
