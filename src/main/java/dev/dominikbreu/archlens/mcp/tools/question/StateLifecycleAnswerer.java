package dev.dominikbreu.archlens.mcp.tools.question;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.GraphQuery.GraphEdge;
import dev.dominikbreu.archlens.cache.GraphQuery.GraphNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Answers the {@code state_lifecycle} intent: writers, readers, and handoffs for a shared-state field. */
public final class StateLifecycleAnswerer {

    private StateLifecycleAnswerer() {}

    /**
     * Answers where a shared-state field is written and read.
     *
     * @param graph the graph to query
     * @param args the {@code field} (field name) selector
     * @param recorder the graph-operation recorder
     * @return the state-lifecycle answer
     */
    public static Answer answer(GraphQuery graph, Map<String, Object> args, QueryPlanRecorder recorder) {
        Answer result = new Answer();
        String field = QuestionSupport.first(args, "field", "query");
        if (field == null) {
            result.unresolved.add("missing-subject: provide field");
            result.answer(emptyAnswer());
            return result;
        }
        result.subject(Map.of("field", field));

        Map<String, String> filters = Map.of("fieldName", field);
        recorder.record("findEdges", "label", "WRITES_STATE");
        List<GraphEdge> writeEdges = graph.findEdges("WRITES_STATE", filters, QuestionSupport.DEFAULT_LIMIT);
        recorder.record("findEdges", "label", "READS_STATE");
        List<GraphEdge> readEdges = graph.findEdges("READS_STATE", filters, QuestionSupport.DEFAULT_LIMIT);
        recorder.record("findEdges", "label", "STATE_HANDOFF");
        List<GraphEdge> handoffEdges = graph.findEdges("STATE_HANDOFF", filters, QuestionSupport.DEFAULT_LIMIT);

        List<Map<String, Object>> writers = edgeSourceComponents(graph, writeEdges);
        List<Map<String, Object>> readers = edgeSourceComponents(graph, readEdges);
        List<Map<String, Object>> handoffs = handoffEdges.stream()
                .map(edge -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("properties", edge.properties());
                    return map;
                })
                .toList();

        if (writers.isEmpty()) result.unresolved.add("no-writer-resolved-for-field:" + field);
        if (readers.isEmpty()) result.unresolved.add("no-reader-resolved-for-field:" + field);

        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("writers", QuestionSupport.distinct(writers));
        answer.put("readers", QuestionSupport.distinct(readers));
        answer.put("handoffs", handoffs);
        result.answer(answer);
        return result;
    }

    private static List<Map<String, Object>> edgeSourceComponents(GraphQuery graph, List<GraphEdge> edges) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (GraphEdge edge : edges) {
            GraphNode node = graph.node(edge.fromId());
            if (node != null) nodes.add(QuestionSupport.nodeMap(node));
        }
        return nodes;
    }

    private static Map<String, Object> emptyAnswer() {
        return Map.of("writers", List.of(), "readers", List.of(), "handoffs", List.of());
    }
}
