package dev.dominikbreu.archlens.mcp.tools.question;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.GraphQuery.DataFlowPathNode;
import dev.dominikbreu.archlens.cache.GraphQuery.EntrypointNode;
import dev.dominikbreu.archlens.cache.GraphQuery.GraphEdge;
import dev.dominikbreu.archlens.cache.GraphQuery.GraphNode;
import dev.dominikbreu.archlens.cache.GraphQuery.RuntimeFlowNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Answers the {@code consumer_context} intent: inbound binding, upstream producers, and downstream dependencies. */
public final class ConsumerContextAnswerer {

    private ConsumerContextAnswerer() {}

    /**
     * Answers what invokes a consumer entrypoint and what it touches downstream.
     *
     * @param graph the graph to query
     * @param args the {@code entrypoint} selector
     * @param recorder the graph-operation recorder (currently unused by this answerer)
     * @return the consumer-context answer
     */
    public static Answer answer(GraphQuery graph, Map<String, Object> args, QueryPlanRecorder recorder) {
        Answer result = new Answer();
        String ref = QuestionSupport.first(args, "entrypoint", "entrypointId", "entrypointName");
        if (ref == null) {
            result.unresolved.add("missing-entrypoint");
            result.answer(Map.of("inboundBinding", Map.of(), "upstream", List.of(), "downstream", List.of()));
            return result;
        }
        EntrypointNode entrypoint = QuestionSupport.entrypoint(graph, ref, result);
        if (entrypoint == null) {
            result.answer(Map.of("inboundBinding", Map.of(), "upstream", List.of(), "downstream", List.of()));
            return result;
        }
        result.subject(QuestionSupport.nodeMap(entrypoint));
        Map<String, Object> binding = QuestionSupport.nodeMap(entrypoint);
        if (entrypoint.channelName() == null && entrypoint.topic() == null) {
            result.unresolved.add("consumer-binding-destination-not-resolved");
        }
        List<Map<String, Object>> upstream = new ArrayList<>();
        List<Map<String, Object>> downstream = new ArrayList<>();
        for (DataFlowPathNode path : graph.pathsForEntrypoint(entrypoint.id())) {
            for (GraphEdge edge : graph.neighborhood(path.id(), "in", QuestionSupport.DEFAULT_LIMIT)) {
                if (!"WORKFLOW_LINK".equals(edge.label()) && !"LINKS_TO".equals(edge.label())) continue;
                GraphNode sourcePath = graph.node(edge.fromId());
                if (sourcePath instanceof DataFlowPathNode source && source.entrypointId() != null) {
                    GraphNode sourceEntrypoint = graph.entrypoint(source.entrypointId());
                    if (sourceEntrypoint != null) upstream.add(QuestionSupport.nodeMap(sourceEntrypoint));
                }
            }
            graph.pathSinks(path.id()).stream().map(QuestionSupport::nodeMap).forEach(downstream::add);
        }
        graph.runtimeFlowForEntrypoint(entrypoint.id().serialize()).map(RuntimeFlowNode::id).stream()
                .flatMap(flow -> graph.flowSteps(flow).stream())
                .map(QuestionSupport::nodeMap)
                .forEach(downstream::add);
        if (upstream.isEmpty()) result.unresolved.add("no-upstream-producer-linked");
        if (downstream.isEmpty()) result.unresolved.add("no-downstream-flow-or-sink-resolved");
        result.answer(Map.of(
                "inboundBinding", binding,
                "upstream", QuestionSupport.distinct(upstream),
                "downstream", QuestionSupport.distinct(downstream)));
        return result;
    }
}
