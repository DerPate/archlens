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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool that exposes pre-computed data-flow paths from entrypoint parameters to sinks.
 */
public class TraceDataFlowTool {

    private final ModelCache cache;

    public TraceDataFlowTool(ModelCache cache) {
        this.cache = cache;
    }

    public String execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (!graph.isIndexed()) return "No workspace indexed yet. Call index_workspace first.";

            if (!graph.hasCallGraph()) {
                return "No call-graph data available. Re-index the workspace to enable data-flow tracing.";
            }

            List<DataFlowPathNode> paths = graph.allDataFlowPaths();
            paths = filterByEntrypointId(paths, ToolArgs.getString(args, "entrypointId"));
            paths = filterByEntrypointName(paths, ToolArgs.getString(args, "entrypointName"), graph);
            paths = filterByParam(paths, ToolArgs.getString(args, "param"));
            paths = filterBySinkKind(paths, ToolArgs.getString(args, "sinkKind"), graph);

            if (paths.isEmpty()) return "No data-flow paths found for the given filters.";

            return format(paths, graph);
        } catch (Exception e) {
            return "Error tracing data flow: " + e.getMessage();
        }
    }

    private static List<DataFlowPathNode> filterByEntrypointId(List<DataFlowPathNode> paths, String epFilter) {
        if (epFilter == null) return paths;
        return paths.stream()
                .filter(p -> p.entrypointId() != null
                        && (p.entrypointId().serialize().equals(epFilter)
                                || p.entrypointId().serialize().contains(epFilter)))
                .toList();
    }

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

    private static boolean entrypointNameMatches(
            DataFlowPathNode p, GraphQuery graph, String methodFilter, String pathFilter, String lower) {
        if (p.entrypointId() == null) return false;
        EntrypointNode ep = graph.entrypoint(p.entrypointId()) instanceof EntrypointNode en ? en : null;
        if (ep == null) return false;
        if (methodFilter != null && !methodFilter.equalsIgnoreCase(ep.httpMethod())) return false;
        return ep.name().toLowerCase().contains(lower) || RuntimeFlowInferrer.pathPrefixMatches(ep.path(), pathFilter);
    }

    private static List<DataFlowPathNode> filterByParam(List<DataFlowPathNode> paths, String paramFilter) {
        if (paramFilter == null) return paths;
        return paths.stream()
                .filter(p -> p.trackedParam() != null
                        && (p.trackedParam().equals(paramFilter)
                                || p.trackedParam().contains(paramFilter)))
                .toList();
    }

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

    private String format(List<DataFlowPathNode> paths, GraphQuery graph) {
        StringBuilder sb = new StringBuilder();
        sb.append(paths.size()).append(" data-flow path(s):\n\n");
        for (DataFlowPathNode path : paths) {
            formatPath(sb, path, graph);
        }
        return sb.toString();
    }

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

    private static String entrypointLabel(DataFlowPathNode path, GraphQuery graph) {
        if (path.entrypointId() == null) return "";
        EntrypointNode ep = graph.entrypoint(path.entrypointId()) instanceof EntrypointNode en ? en : null;
        if (ep != null) {
            return (ep.httpMethod() != null ? ep.httpMethod() + " " : "") + (ep.path() != null ? ep.path() : ep.name());
        }
        return path.entrypointId().serialize();
    }

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

    private static String nodeLabel(DataFlowNodeNode node) {
        String component = node.componentName() != null ? node.componentName() : "";
        String method = node.method() != null ? node.method() : "";
        if (!component.isBlank() && !method.isBlank()) return component + "." + method;
        if (!component.isBlank()) return component;
        return method;
    }

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

    private static String sourceLabel(DataFlowBranchNode branch) {
        Map<String, Object> props = branch.properties();
        Object file = props.get("sourceFile");
        if (file == null || "unknown".equals(file)) return "";
        String f = String.valueOf(file);
        int slash = f.lastIndexOf('/');
        return " " + (slash >= 0 ? f.substring(slash + 1) : f) + ":" + props.get("sourceLine");
    }

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
            appendSinkSource(sb, sink);
            sb.append("\n");
        }
    }

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
