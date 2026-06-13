package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.cache.ToolModelIndex;
import dev.dominikbreu.spoonmcp.extractor.RuntimeFlowInferrer;
import dev.dominikbreu.spoonmcp.model.*;
import java.util.List;
import java.util.Map;

/**
 * MCP tool that exposes pre-computed data-flow paths from entrypoint parameters to sinks.
 *
 * <p>Sinks are classified as {@code persistence}, {@code messaging}, {@code http-outbound},
 * {@code event-bus}, or {@code store}.  The tool requires the workspace to be indexed with
 * call-graph data; without it the paths list will be empty.
 */
public class TraceDataFlowTool {

    private final ModelCache cache;

    /**
     * Creates the tool.
     *
     * @param cache shared model cache
     */
    public TraceDataFlowTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Traces data-flow paths from the requested entrypoint.
     *
     * @param args tool arguments ({@code entrypointId} or {@code entrypointName})
     * @return formatted data-flow report, or an error message
     */
    public String execute(Map<String, Object> args) {
        try {
            ToolModelIndex index = cache.index();
            ArchitectureModel model = index.rawModel();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";

            if (model.callEdges.isEmpty()) {
                return "No call-graph data available. Re-index the workspace to enable data-flow tracing.";
            }

            List<DataFlowPath> paths = model.dataFlowPaths;
            paths = filterByEntrypointId(paths, ToolArgs.getString(args, "entrypointId"));
            paths = filterByEntrypointName(paths, ToolArgs.getString(args, "entrypointName"), index);
            paths = filterByParam(paths, ToolArgs.getString(args, "param"));
            paths = filterBySinkKind(paths, ToolArgs.getString(args, "sinkKind"));

            if (paths.isEmpty()) return "No data-flow paths found for the given filters.";

            return format(paths, index);
        } catch (Exception e) {
            return "Error tracing data flow: " + e.getMessage();
        }
    }

    private static List<DataFlowPath> filterByEntrypointId(List<DataFlowPath> paths, String epFilter) {
        if (epFilter == null) return paths;
        return paths.stream()
                .filter(p -> p.entrypointId != null
                        && (p.entrypointId.serialize().equals(epFilter)
                                || p.entrypointId.serialize().contains(epFilter)))
                .toList();
    }

    private static List<DataFlowPath> filterByEntrypointName(
            List<DataFlowPath> paths, String nameFilter, ToolModelIndex index) {
        if (nameFilter == null) return paths;
        String methodFilter = RuntimeFlowInferrer.extractMethodFromRef(nameFilter);
        String pathFilter = RuntimeFlowInferrer.extractPathFromRef(nameFilter);
        String lower = pathFilter.toLowerCase();
        return paths.stream()
                .filter(p -> entrypointNameMatches(p, index, methodFilter, pathFilter, lower))
                .toList();
    }

    private static boolean entrypointNameMatches(
            DataFlowPath p, ToolModelIndex index, String methodFilter, String pathFilter, String lower) {
        Entrypoint ep = p.entrypointId != null ? index.entrypoint(p.entrypointId) : null;
        if (ep == null) return false;
        if (methodFilter != null && !methodFilter.equalsIgnoreCase(ep.httpMethod)) return false;
        return ep.name.toLowerCase().contains(lower) || RuntimeFlowInferrer.pathPrefixMatches(ep.path, pathFilter);
    }

    private static List<DataFlowPath> filterByParam(List<DataFlowPath> paths, String paramFilter) {
        if (paramFilter == null) return paths;
        return paths.stream()
                .filter(p -> p.trackedParam.equals(paramFilter) || p.trackedParam.contains(paramFilter))
                .toList();
    }

    private static List<DataFlowPath> filterBySinkKind(List<DataFlowPath> paths, String sinkFilter) {
        if (sinkFilter == null) return paths;
        DataFlowSink.Kind filterKind = DataFlowSink.Kind.from(sinkFilter);
        return paths.stream()
                .filter(p -> p.sinks.stream().anyMatch(s -> s.kind == filterKind))
                .toList();
    }

    private String format(List<DataFlowPath> paths, ToolModelIndex index) {
        StringBuilder sb = new StringBuilder();
        sb.append(paths.size()).append(" data-flow path(s):\n\n");
        for (DataFlowPath path : paths) {
            formatPath(sb, path, index);
        }
        return sb.toString();
    }

    private void formatPath(StringBuilder sb, DataFlowPath path, ToolModelIndex index) {
        sb.append("## ")
                .append(entrypointLabel(path, index))
                .append(" → param: ")
                .append(path.trackedParam)
                .append("\n");
        sb.append("  id: ").append(path.id.serialize()).append("\n");
        formatSteps(sb, path);
        formatSinks(sb, path);
        sb.append("\n");
    }

    private static String entrypointLabel(DataFlowPath path, ToolModelIndex index) {
        Entrypoint ep = path.entrypointId != null ? index.entrypoint(path.entrypointId) : null;
        if (ep != null) {
            return (ep.httpMethod != null ? ep.httpMethod + " " : "") + (ep.path != null ? ep.path : ep.name);
        }
        return path.entrypointId != null ? path.entrypointId.serialize() : "";
    }

    private static void formatSteps(StringBuilder sb, DataFlowPath path) {
        if (path.steps.isEmpty()) return;
        sb.append("  flow:\n");
        for (DataFlowStep step : path.steps) {
            sb.append("    ")
                    .append(step.index + 1)
                    .append(". ")
                    .append(step.componentName)
                    .append(".")
                    .append(step.method)
                    .append(" (as '")
                    .append(step.localName)
                    .append("')\n");
        }
    }

    private static void formatSinks(StringBuilder sb, DataFlowPath path) {
        if (path.sinks.isEmpty()) return;
        sb.append("  sinks:\n");
        for (DataFlowSink sink : path.sinks) {
            sb.append("    - [")
                    .append(sink.kind)
                    .append("] ")
                    .append(sink.componentName)
                    .append(".")
                    .append(sink.method);
            if (sink.kind == DataFlowSink.Kind.STORE && sink.fieldName != null) {
                sb.append("  field=").append(sink.fieldName);
            }
            appendSinkSource(sb, sink);
            sb.append("\n");
        }
    }

    private static void appendSinkSource(StringBuilder sb, DataFlowSink sink) {
        if (sink.source == null || sink.source.file == null || "unknown".equals(sink.source.file)) return;
        String file = sink.source.file;
        int slash = file.lastIndexOf('/');
        sb.append("  (")
                .append(slash >= 0 ? file.substring(slash + 1) : file)
                .append(":")
                .append(sink.source.line)
                .append(")");
    }
}
