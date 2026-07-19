package dev.dominikbreu.archlens.mcp.tools.question;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.GraphQuery.ComponentNode;
import dev.dominikbreu.archlens.cache.GraphQuery.ExternalSystemNode;
import dev.dominikbreu.archlens.cache.GraphQuery.GraphEdge;
import dev.dominikbreu.archlens.cache.GraphQuery.GraphNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Answers the {@code external_integration_context} intent: callers, configured destination, and replacement impact. */
public final class ExternalIntegrationContextAnswerer {

    private ExternalIntegrationContextAnswerer() {}

    /**
     * Answers an outbound-integration question: given an {@code ExternalSystem} (or the
     * component that talks to it), reports its configured base URL when resolvable, its
     * callers, and what may break if it's replaced.
     *
     * @param graph the graph to query
     * @param args {@code component} (an {@code ExternalSystem} name or its owning client component) and optional {@code maxDepth}
     * @param recorder the graph-operation recorder
     * @return the external-integration-context answer
     */
    public static Answer answer(GraphQuery graph, Map<String, Object> args, QueryPlanRecorder recorder) {
        Answer result = new Answer();
        String ref = QuestionSupport.first(args, "component", "componentId", "query");
        if (ref == null) {
            result.unresolved.add("missing-subject: provide component");
            result.answer(emptyAnswer());
            return result;
        }

        List<ExternalSystemNode> matches =
                graph.findNodes("ExternalSystem", ref, Map.of(), QuestionSupport.DEFAULT_LIMIT).stream()
                        .filter(ExternalSystemNode.class::isInstance)
                        .map(ExternalSystemNode.class::cast)
                        .toList();
        if (matches.size() == 1) {
            return forExternalSystem(graph, matches.getFirst(), args, recorder);
        }
        if (matches.size() > 1) {
            result.ambiguous.add("external-system-reference-matched-" + matches.size() + "-nodes:" + ref);
            result.answer(emptyAnswer());
            return result;
        }

        ComponentNode component = QuestionSupport.component(graph, ref, result);
        if (component == null) {
            result.answer(emptyAnswer());
            return result;
        }
        return reverseFromClientComponent(graph, component, args, recorder);
    }

    private static Answer forExternalSystem(
            GraphQuery graph, ExternalSystemNode system, Map<String, Object> args, QueryPlanRecorder recorder) {
        Answer result = new Answer();
        result.subject(QuestionSupport.nodeMap(system));
        recorder.record("outgoingNodes", "label", "CONFIGURED_BY");
        List<GraphNode> configs = QuestionSupport.outgoingNodes(graph, system.id(), "CONFIGURED_BY");
        Map<String, Object> configuredDestination =
                configs.isEmpty() ? Map.of() : QuestionSupport.nodeMap(configs.getFirst());
        if (configs.isEmpty()) result.unresolved.add("base-url-not-modeled");

        int depth = QuestionSupport.integer(args, "maxDepth", 4);
        List<Map<String, Object>> callers = new ArrayList<>();
        List<Map<String, Object>> replacementImpact = new ArrayList<>();
        recorder.record("neighborhood", "id", system.id().serialize());
        for (GraphEdge edge : graph.neighborhood(system.id(), "in", QuestionSupport.DEFAULT_LIMIT)) {
            if (!"DEPENDS_ON".equals(edge.label())) continue;
            GraphNode client = graph.node(edge.fromId());
            if (client == null) continue;
            recorder.record("impactedBy", "component", client.id().serialize());
            for (GraphNode impacted : graph.impactedBy(client.id(), depth, QuestionSupport.DEFAULT_LIMIT)) {
                if ("Entrypoint".equals(impacted.label())) callers.add(QuestionSupport.nodeMap(impacted));
                else replacementImpact.add(QuestionSupport.nodeMap(impacted));
            }
        }
        if (callers.isEmpty()) result.unresolved.add("no-caller-resolved");

        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("configuredDestination", configuredDestination);
        answer.put("dataSentReceived", Map.of());
        answer.put("callers", QuestionSupport.distinct(callers));
        answer.put("replacementImpact", QuestionSupport.distinct(replacementImpact));
        result.answer(answer);
        result.unresolved.add("data-sent-received-not-modeled");
        return result;
    }

    private static Answer reverseFromClientComponent(
            GraphQuery graph, ComponentNode component, Map<String, Object> args, QueryPlanRecorder recorder) {
        Answer result = new Answer();
        result.subject(QuestionSupport.nodeMap(component));
        int depth = QuestionSupport.integer(args, "maxDepth", 4);
        recorder.record("impactedBy", "component", component.id().serialize());
        List<Map<String, Object>> callers = new ArrayList<>();
        List<Map<String, Object>> replacementImpact = new ArrayList<>();
        for (GraphNode impacted : graph.impactedBy(component.id(), depth, QuestionSupport.DEFAULT_LIMIT)) {
            if ("Entrypoint".equals(impacted.label())) callers.add(QuestionSupport.nodeMap(impacted));
            else replacementImpact.add(QuestionSupport.nodeMap(impacted));
        }
        if (callers.isEmpty()) result.unresolved.add("no-caller-resolved");
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("configuredDestination", Map.of());
        answer.put("dataSentReceived", Map.of());
        answer.put("callers", QuestionSupport.distinct(callers));
        answer.put("replacementImpact", QuestionSupport.distinct(replacementImpact));
        result.answer(answer);
        result.unresolved.add("base-url-not-modeled");
        result.unresolved.add("data-sent-received-not-modeled");
        return result;
    }

    private static Map<String, Object> emptyAnswer() {
        return Map.of(
                "configuredDestination", Map.of(),
                "dataSentReceived", Map.of(),
                "callers", List.of(),
                "replacementImpact", List.of());
    }
}
