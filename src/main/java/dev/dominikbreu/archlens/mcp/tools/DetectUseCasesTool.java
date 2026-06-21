package dev.dominikbreu.archlens.mcp.tools;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.extractor.UseCaseDetector;
import dev.dominikbreu.archlens.model.UseCase;
import dev.dominikbreu.archlens.model.UseCaseNamingConfig;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.databind.ObjectMapper;

/**
 * MCP tool that detects business use cases from indexed entrypoints and their call chains.
 */
public class DetectUseCasesTool {

    private final ModelCache cache;
    private final UseCaseDetector detector = new UseCaseDetector();
    private final ObjectMapper mapper = new ObjectMapper();

    public DetectUseCasesTool(ModelCache cache) {
        this.cache = cache;
    }

    public String execute(Map<String, Object> args) {
        try {
            GraphQuery graph = cache.graph();
            if (!graph.isIndexed()) return "No workspace indexed yet. Call index_workspace first.";

            ConfigResult configResult = resolveConfig(ToolArgs.getString(args, "configFile"));
            if (configResult.error() != null) return configResult.error();

            String filterModule = ToolArgs.getString(args, "module");
            int maxDepth = ToolArgs.getInt(args, "maxDepth", 5);

            List<UseCase> useCases = detector.detect(graph, configResult.config());

            if (filterModule != null) useCases = filterByModule(useCases, graph, filterModule);
            if (useCases.isEmpty()) return "No use cases detected.";

            return format(useCases, graph, maxDepth);
        } catch (Exception e) {
            return "Error detecting use cases: " + e.getMessage();
        }
    }

    private record ConfigResult(UseCaseNamingConfig config, String error) {}

    private ConfigResult resolveConfig(String configFile) {
        if (configFile == null) return new ConfigResult(UseCaseNamingConfig.empty(), null);
        try {
            return new ConfigResult(UseCaseNamingConfig.loadFrom(mapper, configFile), null);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new ConfigResult(null, "Error: could not load naming config — " + msg);
        }
    }

    private List<UseCase> filterByModule(List<UseCase> useCases, GraphQuery graph, String module) {
        return useCases.stream()
                .filter(uc -> {
                    GraphQuery.GraphNode epNode = uc.entrypointId != null
                            ? graph.entrypoint(uc.entrypointId) : null;
                    if (!(epNode instanceof GraphQuery.EntrypointNode ep)) return false;
                    GraphQuery.GraphNode compNode = ep.componentId() != null
                            ? graph.component(ep.componentId()) : null;
                    if (!(compNode instanceof GraphQuery.ComponentNode comp)) return false;
                    return comp.module() != null
                            && (module.equals(comp.module().serialize())
                                    || comp.module().serialize().contains(module));
                })
                .toList();
    }

    private String format(List<UseCase> useCases, GraphQuery graph, int maxDepth) {
        StringBuilder sb = new StringBuilder();
        sb.append("Detected ").append(useCases.size()).append(" use case(s)");
        sb.append(graph.hasCallGraph()
                ? ":\n\n"
                : " (injection-edge fallback — re-index for call-graph accuracy):\n\n");

        for (UseCase uc : useCases) {
            sb.append("## ").append(uc.name).append("\n");
            sb.append("  id:           ").append(uc.id.serialize()).append("\n");
            sb.append("  type:         ").append(uc.type).append("\n");
            if (uc.channelOrPath != null) sb.append("  channel/path: ").append(uc.channelOrPath).append("\n");
            sb.append("  components:   ").append(resolveNames(uc.componentIds, graph)).append("\n");
            if (!uc.methodChain.isEmpty()) {
                int shown = Math.min(uc.methodChain.size(), maxDepth * 2);
                sb.append("  call chain:\n");
                for (int i = 0; i < shown; i++) sb.append("    - ").append(uc.methodChain.get(i)).append("\n");
                if (uc.methodChain.size() > shown) {
                    sb.append("    ... (").append(uc.methodChain.size() - shown).append(" more)\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String resolveNames(List<ComponentId> componentIds, GraphQuery graph) {
        return componentIds.stream()
                .map(id -> {
                    GraphQuery.GraphNode n = graph.component(id);
                    return n instanceof GraphQuery.ComponentNode cn ? cn.name() : id.serialize();
                })
                .collect(Collectors.joining(", "));
    }
}
