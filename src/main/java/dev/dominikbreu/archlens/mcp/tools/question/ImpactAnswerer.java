package dev.dominikbreu.archlens.mcp.tools.question;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.GraphQuery.ComponentNode;
import dev.dominikbreu.archlens.cache.GraphQuery.GraphNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Answers the {@code impact} intent: nodes grouped by architecture concern, with evidence chains. */
public final class ImpactAnswerer {

    private ImpactAnswerer() {}

    /**
     * Answers what may break if the given component is replaced.
     *
     * @param graph the graph to query
     * @param args the {@code component} selector and optional {@code maxDepth}
     * @param recorder the graph-operation recorder (currently unused by this answerer)
     * @return the impact answer
     */
    public static Answer answer(GraphQuery graph, Map<String, Object> args, QueryPlanRecorder recorder) {
        Answer result = new Answer();
        String ref = QuestionSupport.first(args, "component", "componentId", "query");
        ComponentNode target = ref != null ? QuestionSupport.component(graph, ref, result) : null;
        if (target == null) {
            if (ref == null) result.unresolved.add("missing-component");
            result.answer(emptyImpact());
            return result;
        }
        result.subject(QuestionSupport.nodeMap(target));
        int depth = QuestionSupport.integer(args, "maxDepth", 4);
        List<GraphNode> impacted = graph.impactedBy(target.id(), depth, QuestionSupport.DEFAULT_LIMIT);
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        groups.put("entrypoints", new ArrayList<>());
        groups.put("workflows", new ArrayList<>());
        groups.put("persistence", new ArrayList<>());
        groups.put("externalIntegrations", new ArrayList<>());
        groups.put("components", new ArrayList<>());
        for (GraphNode node : impacted) {
            String group =
                    switch (node.label()) {
                        case "Entrypoint" -> "entrypoints";
                        case "RuntimeFlow", "RuntimeFlowStep", "DataFlowPath", "DataFlowSink", "PipelineChain" ->
                            "workflows";
                        case "PersistenceUnit", "DataSource", "PersistenceOperation", "TransactionBoundary" ->
                            "persistence";
                        case "ExternalSystem", "Interface" -> "externalIntegrations";
                        default -> "components";
                    };
            groups.get(group).add(QuestionSupport.nodeMap(node));
        }
        List<Map<String, Object>> evidenceChains = new ArrayList<>();
        for (GraphNode node : impacted.stream().limit(50).toList()) {
            graph.paths(node.id(), target.id(), depth, 1).stream()
                    .map(QuestionSupport::pathMap)
                    .forEach(evidenceChains::add);
        }
        Map<String, Object> answer = new LinkedHashMap<>(groups);
        answer.put("evidenceChains", evidenceChains);
        result.answer(answer);
        if (impacted.isEmpty()) result.unresolved.add("no-impacted-nodes-found");
        return result;
    }

    private static Map<String, Object> emptyImpact() {
        return Map.of(
                "entrypoints", List.of(),
                "workflows", List.of(),
                "persistence", List.of(),
                "externalIntegrations", List.of(),
                "components", List.of(),
                "evidenceChains", List.of());
    }
}
