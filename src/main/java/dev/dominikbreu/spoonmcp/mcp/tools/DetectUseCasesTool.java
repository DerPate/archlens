package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.cache.ToolModelIndex;
import dev.dominikbreu.spoonmcp.extractor.UseCaseDetector;
import dev.dominikbreu.spoonmcp.model.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.databind.ObjectMapper;

/**
 * MCP tool that detects business use cases from indexed entrypoints and their call chains.
 *
 * <p>Accepts an optional {@code configFile} parameter pointing to a JSON file with
 * human-readable name mappings (see {@link UseCaseNamingConfig}).
 */
public class DetectUseCasesTool {

    private final ModelCache cache;
    private final UseCaseDetector detector = new UseCaseDetector();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Creates the tool with the shared model cache.
     *
     * @param cache model cache used by prior indexing
     */
    public DetectUseCasesTool(ModelCache cache) {
        this.cache = cache;
    }

    /**
     * Executes use case detection.
     *
     * @param args JSON arguments
     * @return formatted use case list or an error message
     */
    public String execute(Map<String, Object> args) {
        try {
            ToolModelIndex index = cache.index();
            ArchitectureModel model = index.rawModel();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";

            ConfigResult configResult = resolveConfig(ToolArgs.getString(args, "configFile"));
            if (configResult.error() != null) return configResult.error();
            UseCaseNamingConfig config = configResult.config();

            String filterModule = ToolArgs.getString(args, "module");
            int maxDepth = ToolArgs.getInt(args, "maxDepth", 5);

            List<UseCase> useCases = detector.detect(model, config);

            if (filterModule != null) {
                useCases = filterByModule(useCases, index, filterModule);
            }

            if (useCases.isEmpty()) return "No use cases detected.";

            return format(useCases, index, maxDepth);
        } catch (Exception e) {
            return "Error detecting use cases: " + e.getMessage();
        }
    }

    /** Resolved naming config, or a user-facing error string when loading failed. */
    private record ConfigResult(UseCaseNamingConfig config, String error) {}

    private ConfigResult resolveConfig(String configFile) {
        if (configFile == null) {
            return new ConfigResult(UseCaseNamingConfig.empty(), null);
        }
        try {
            return new ConfigResult(UseCaseNamingConfig.loadFrom(mapper, configFile), null);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new ConfigResult(null, "Error: could not load naming config — " + msg);
        }
    }

    private List<UseCase> filterByModule(List<UseCase> useCases, ToolModelIndex index, String module) {
        return useCases.stream()
                .filter(uc -> {
                    Entrypoint ep = uc.entrypointId != null ? index.entrypoint(uc.entrypointId) : null;
                    if (ep == null) return false;
                    Component comp = index.component(ep.componentId);
                    return comp != null
                            && comp.module != null
                            && (module.equals(comp.module.serialize())
                                    || comp.module.serialize().contains(module));
                })
                .toList();
    }

    private String format(List<UseCase> useCases, ToolModelIndex index, int maxDepth) {
        ArchitectureModel model = index.rawModel();
        StringBuilder sb = new StringBuilder();
        sb.append("Detected ").append(useCases.size()).append(" use case(s)");
        sb.append(
                model.callEdges.isEmpty()
                        ? " (injection-edge fallback — re-index for call-graph accuracy):\n\n"
                        : ":\n\n");

        for (UseCase uc : useCases) {
            sb.append("## ").append(uc.name).append("\n");
            sb.append("  id:           ").append(uc.id.serialize()).append("\n");
            sb.append("  type:         ").append(uc.type).append("\n");
            if (uc.channelOrPath != null) {
                sb.append("  channel/path: ").append(uc.channelOrPath).append("\n");
            }
            sb.append("  components:   ")
                    .append(resolveNames(uc.componentIds, index))
                    .append("\n");
            if (!uc.methodChain.isEmpty()) {
                int shown = Math.min(uc.methodChain.size(), maxDepth * 2);
                sb.append("  call chain:\n");
                for (int i = 0; i < shown; i++) {
                    sb.append("    - ").append(uc.methodChain.get(i)).append("\n");
                }
                if (uc.methodChain.size() > shown) {
                    sb.append("    ... (").append(uc.methodChain.size() - shown).append(" more)\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String resolveNames(
            List<dev.dominikbreu.spoonmcp.model.ids.ComponentId> componentIds, ToolModelIndex index) {
        return componentIds.stream()
                .map(id -> {
                    Component c = index.component(id);
                    return c != null ? c.name : id.serialize();
                })
                .collect(Collectors.joining(", "));
    }
}
