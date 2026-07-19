package dev.dominikbreu.archlens.mcp.tools.question;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.GraphQuery.ConfigPropertyNode;
import dev.dominikbreu.archlens.cache.GraphQuery.GraphEdge;
import dev.dominikbreu.archlens.cache.GraphQuery.GraphNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Answers the {@code configuration_context} intent: config key declarations, resolved values, and usages. */
public final class ConfigurationContextAnswerer {

    private ConfigurationContextAnswerer() {}

    /**
     * Answers what a configuration key resolves to and where it's used.
     *
     * @param graph the graph to query
     * @param args the config key {@code query} (or {@code key})
     * @param recorder the graph-operation recorder
     * @return the configuration-context answer
     */
    public static Answer answer(GraphQuery graph, Map<String, Object> args, QueryPlanRecorder recorder) {
        Answer result = new Answer();
        String key = QuestionSupport.first(args, "query", "key");
        if (key == null) {
            result.unresolved.add("missing-subject: provide a config key");
            result.answer(emptyAnswer());
            return result;
        }
        recorder.record("findNodes", "label", "ConfigProperty");
        List<ConfigPropertyNode> declarations =
                graph.findNodes("ConfigProperty", key, Map.of(), QuestionSupport.DEFAULT_LIMIT).stream()
                        .filter(ConfigPropertyNode.class::isInstance)
                        .map(ConfigPropertyNode.class::cast)
                        .toList();
        if (declarations.isEmpty()) {
            result.unresolved.add("config-key-not-declared:" + key);
            result.answer(emptyAnswer());
            return result;
        }
        result.subject(QuestionSupport.nodeMap(declarations.getFirst()));
        if (declarations.size() > 1) {
            result.ambiguous.add("config-key-declared-in-" + declarations.size() + "-modules:" + key);
        }

        List<Map<String, Object>> usages = new ArrayList<>();
        for (ConfigPropertyNode declaration : declarations) {
            recorder.record("neighborhood", "id", declaration.id().serialize());
            for (GraphEdge edge : graph.neighborhood(declaration.id(), "in", QuestionSupport.DEFAULT_LIMIT)) {
                if (!"CONFIGURED_BY".equals(edge.label())) continue;
                GraphNode user = graph.node(edge.fromId());
                if (user != null) usages.add(QuestionSupport.nodeMap(user));
            }
            if (!declaration.resolved()) result.unresolved.add("unexpanded-placeholder:" + declaration.key());
        }
        if (usages.isEmpty()) result.unresolved.add("no-usages-found:" + key);

        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put(
                "declarations",
                declarations.stream().map(QuestionSupport::nodeMap).toList());
        answer.put("usages", QuestionSupport.distinct(usages));
        result.answer(answer);
        return result;
    }

    private static Map<String, Object> emptyAnswer() {
        return Map.of("declarations", List.of(), "usages", List.of());
    }
}
