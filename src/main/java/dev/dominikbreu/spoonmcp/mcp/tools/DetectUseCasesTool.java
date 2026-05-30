package dev.dominikbreu.spoonmcp.mcp.tools;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
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
            ArchitectureModel model = cache.load();
            if (model == null) return "No workspace indexed yet. Call index_workspace first.";

            String configFile = ToolArgs.getString(args, "configFile");
            UseCaseNamingConfig config;
            if (configFile == null) {
                config = UseCaseNamingConfig.empty();
            } else {
                try {
                    config = UseCaseNamingConfig.loadFrom(mapper, configFile);
                } catch (Exception e) {
                    String msg;
                    if (e.getMessage() != null) {
                        msg = e.getMessage();
                    } else {
                        msg = e.getClass().getSimpleName();
                    }
                    return "Error: could not load naming config — " + msg;
                }
            }

            String filterModule = ToolArgs.getString(args, "module");
            int maxDepth = ToolArgs.getInt(args, "maxDepth", 5);

            List<UseCase> useCases = detector.detect(model, config);

            if (filterModule != null) {
                useCases = filterByModule(useCases, model, filterModule);
            }

            if (useCases.isEmpty()) return "No use cases detected.";

            return format(useCases, model, maxDepth);
        } catch (Exception e) {
            return "Error detecting use cases: " + e.getMessage();
        }
    }

    private List<UseCase> filterByModule(List<UseCase> useCases, ArchitectureModel model, String module) {
        return useCases.stream()
                .filter(uc -> {
                    Entrypoint ep = model.entrypoints.stream()
                            .filter(e -> uc.entrypointId != null && uc.entrypointId.equals(e.id))
                            .findFirst()
                            .orElse(null);
                    if (ep == null) return false;
                    return model.components.stream()
                            .filter(c -> c.id.equals(ep.componentId))
                            .anyMatch(c -> c.module != null
                                    && (module.equals(c.module.serialize())
                                            || c.module.serialize().contains(module)));
                })
                .collect(Collectors.toList());
    }

    private String format(List<UseCase> useCases, ArchitectureModel model, int maxDepth) {
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
                    .append(resolveNames(uc.componentIds, model))
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
            List<dev.dominikbreu.spoonmcp.model.ids.ComponentId> componentIds, ArchitectureModel model) {
        return componentIds.stream()
                .map(id -> model.components.stream()
                        .filter(c -> c.id.equals(id))
                        .map(c -> c.name)
                        .findFirst()
                        .orElse(id.serialize()))
                .collect(Collectors.joining(", "));
    }
}
