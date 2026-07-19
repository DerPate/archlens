package dev.dominikbreu.archlens.mcp.tools.question;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.GraphQuery.GraphNode;
import dev.dominikbreu.archlens.model.ids.GraphNodeId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Answers the generic {@code relationship} intent: a bounded neighborhood or path around a subject that doesn't need a specialized contract. */
public final class RelationshipAnswerer {

    private RelationshipAnswerer() {}

    /**
     * Answers a generic relationship question via a bounded neighborhood (and, when a second
     * subject is given, the paths between them).
     *
     * @param graph the graph to query
     * @param args {@code entrypoint}/{@code component}/{@code query} for the primary subject, optional {@code target} for a second subject, optional {@code maxDepth}
     * @param recorder the graph-operation recorder
     * @return the relationship answer
     */
    public static Answer answer(GraphQuery graph, Map<String, Object> args, QueryPlanRecorder recorder) {
        Answer result = new Answer();
        String ref = QuestionSupport.first(args, "entrypoint", "component", "query");
        if (ref == null) {
            result.unresolved.add("missing-subject");
            result.answer(Map.of("neighborhood", Map.of(), "paths", List.of()));
            return result;
        }
        GraphNodeId subjectId = resolve(graph, ref, result);
        if (subjectId == null) {
            result.answer(Map.of("neighborhood", Map.of(), "paths", List.of()));
            return result;
        }
        GraphNode subjectNode = graph.node(subjectId);
        result.subject(subjectNode != null ? QuestionSupport.nodeMap(subjectNode) : Map.of());

        int depth = QuestionSupport.integer(args, "maxDepth", 2);
        recorder.record("reachable", "id", subjectId.serialize());
        List<GraphNode> neighborhoodNodes =
                graph.reachable(subjectId, "both", null, depth, QuestionSupport.DEFAULT_LIMIT);
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (GraphNode node : neighborhoodNodes) {
            grouped.computeIfAbsent(node.label(), k -> new ArrayList<>()).add(QuestionSupport.nodeMap(node));
        }

        String targetRef = QuestionSupport.first(args, "target");
        List<Map<String, Object>> paths = List.of();
        if (targetRef != null) {
            GraphNodeId targetId = resolve(graph, targetRef, result);
            if (targetId != null) {
                recorder.record("paths", Map.of("from", subjectId.serialize(), "to", targetId.serialize()));
                paths = graph.paths(subjectId, targetId, depth, QuestionSupport.DEFAULT_LIMIT).stream()
                        .map(QuestionSupport::pathMap)
                        .toList();
            }
        }
        if (neighborhoodNodes.isEmpty()) result.unresolved.add("no-neighborhood-found");

        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("neighborhood", grouped);
        answer.put("paths", paths);
        result.answer(answer);
        return result;
    }

    private static GraphNodeId resolve(GraphQuery graph, String ref, Answer result) {
        Optional<GraphNodeId> entrypoint = graph.resolveEntrypoint(ref);
        if (entrypoint.isPresent()) return entrypoint.get();
        Optional<GraphNodeId> component = graph.resolveComponent(ref);
        if (component.isPresent()) return component.get();
        result.unresolved.add("subject-not-resolved:" + ref);
        return null;
    }
}
