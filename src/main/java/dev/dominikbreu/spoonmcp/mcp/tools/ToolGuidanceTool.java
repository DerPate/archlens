package dev.dominikbreu.spoonmcp.mcp.tools;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Returns compact task-to-tool recipes for agents.
 */
public class ToolGuidanceTool {

    /**
     * Executes the guidance query.
     *
     * @param args optional task filter
     * @return guidance text
     */
    public String execute(Map<String, Object> args) {
        String task = Objects.toString(args.getOrDefault("task", ""), "").toLowerCase(Locale.ROOT);
        List<Recipe> recipes = recipes().stream()
                .filter(recipe -> task.isBlank() || recipe.matches(task))
                .toList();
        if (recipes.isEmpty()) {
            recipes = recipes();
        }
        StringBuilder sb = new StringBuilder("Tool guidance:\n");
        for (Recipe recipe : recipes) {
            sb.append("- ").append(recipe.title()).append("\n");
            sb.append("  Use: ").append(recipe.use()).append("\n");
            sb.append("  Avoid: ").append(recipe.avoid()).append("\n");
        }
        return sb.toString();
    }

    private static List<Recipe> recipes() {
        return List.of(
                new Recipe(
                        "Find high-signal business flow",
                        "query_architecture_graph action=find_nodes label=Component filters={agentCategory=core-workflow}; inspect primaryRole, supportRole, classificationEvidence",
                        "raw fan-in ranking without checking agentCategory or noiseScore",
                        "business workflow core-workflow component"),
                new Recipe(
                        "Investigate one class",
                        "find_components query=<name>; get_component_dependencies component=<id>; query_architecture_graph action=neighborhood nodeId=<id>",
                        "guessing dependencies from package names only",
                        "class component dependency investigate"),
                new Recipe(
                        "Trace request or pipeline behavior",
                        "find_entrypoints; get_runtime_flow; trace_data_flow; render_pipeline when WORKFLOW_LINK or linked sinks are present",
                        "drawing pipeline edges that are not in graph/data-flow output",
                        "request pipeline entrypoint data-flow trace"),
                new Recipe(
                        "Avoid support noise",
                        "filter agentCategory=core-workflow|boundary first; query supportRole explicitly when infrastructure matters",
                        "treating supportRole=configuration|mapper|redis-lock|migration-initializer as business flow by default",
                        "noise support infrastructure mapper config"),
                new Recipe(
                        "Debug graph metadata",
                        "query_architecture_graph summary; find_nodes; find_edges; neighborhood; inspect classificationEvidence before changing code",
                        "inventing nodes or edges not exposed by graph tools",
                        "debug graph metadata evidence"));
    }

    private record Recipe(String title, String use, String avoid, String keywords) {
        boolean matches(String task) {
            String haystack = (title + " " + use + " " + keywords).toLowerCase(Locale.ROOT);
            return haystack.contains(task);
        }
    }
}
