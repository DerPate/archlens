package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.extractor.RuntimeFlowInferrer;
import dev.dominikbreu.spoonmcp.model.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
            ArchitectureModel model = cache.load();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";

            if (model.callEdges.isEmpty()) {
                return "No call-graph data available. Re-index the workspace to enable data-flow tracing.";
            }

            List<DataFlowPath> paths = model.dataFlowPaths;

            String epFilter = ToolArgs.getString(args, "entrypointId");
            String nameFilter = ToolArgs.getString(args, "entrypointName");
            String paramFilter = ToolArgs.getString(args, "param");
            String sinkFilter = ToolArgs.getString(args, "sinkKind");

            if (epFilter != null) {
                paths = paths.stream()
                        .filter(p -> p.entrypointId != null
                                && (p.entrypointId.serialize().equals(epFilter)
                                        || p.entrypointId.serialize().contains(epFilter)))
                        .collect(Collectors.toList());
            }
            if (nameFilter != null) {
                String methodFilter = RuntimeFlowInferrer.extractMethodFromRef(nameFilter);
                String pathFilter = RuntimeFlowInferrer.extractPathFromRef(nameFilter);
                String lower = pathFilter.toLowerCase();
                paths = paths.stream()
                        .filter(p -> {
                            Entrypoint ep = model.entrypoints.stream()
                                    .filter(e -> p.entrypointId != null && p.entrypointId.equals(e.id))
                                    .findFirst()
                                    .orElse(null);
                            if (ep == null) return false;
                            if (methodFilter != null && !methodFilter.equalsIgnoreCase(ep.httpMethod)) return false;
                            return ep.name.toLowerCase().contains(lower)
                                    || RuntimeFlowInferrer.pathPrefixMatches(ep.path, pathFilter);
                        })
                        .collect(Collectors.toList());
            }
            if (paramFilter != null) {
                paths = paths.stream()
                        .filter(p -> p.trackedParam.equals(paramFilter) || p.trackedParam.contains(paramFilter))
                        .collect(Collectors.toList());
            }
            if (sinkFilter != null) {
                DataFlowSink.Kind filterKind = DataFlowSink.Kind.from(sinkFilter);
                paths = paths.stream()
                        .filter(p -> p.sinks.stream().anyMatch(s -> s.kind == filterKind))
                        .collect(Collectors.toList());
            }

            if (paths.isEmpty()) return "No data-flow paths found for the given filters.";

            return format(paths, model);
        } catch (Exception e) {
            return "Error tracing data flow: " + e.getMessage();
        }
    }

    private String format(List<DataFlowPath> paths, ArchitectureModel model) {
        StringBuilder sb = new StringBuilder();
        sb.append(paths.size()).append(" data-flow path(s):\n\n");

        for (DataFlowPath path : paths) {
            Entrypoint ep = model.entrypoints.stream()
                    .filter(e -> path.entrypointId != null && path.entrypointId.equals(e.id))
                    .findFirst()
                    .orElse(null);

            String epLabel = ep != null
                    ? (ep.httpMethod != null ? ep.httpMethod + " " : "") + (ep.path != null ? ep.path : ep.name)
                    : (path.entrypointId != null ? path.entrypointId.serialize() : "");

            sb.append("## ")
                    .append(epLabel)
                    .append(" → param: ")
                    .append(path.trackedParam)
                    .append("\n");
            sb.append("  id: ").append(path.id).append("\n");

            if (!path.steps.isEmpty()) {
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

            if (!path.sinks.isEmpty()) {
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
                    if (sink.source != null && sink.source.file != null && !sink.source.file.equals("unknown")) {
                        String file = sink.source.file;
                        int slash = file.lastIndexOf('/');
                        sb.append("  (")
                                .append(slash >= 0 ? file.substring(slash + 1) : file)
                                .append(":")
                                .append(sink.source.line)
                                .append(")");
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
