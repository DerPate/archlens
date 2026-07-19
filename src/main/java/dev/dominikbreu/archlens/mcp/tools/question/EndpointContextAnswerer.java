package dev.dominikbreu.archlens.mcp.tools.question;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.GraphQuery.ComponentNode;
import dev.dominikbreu.archlens.cache.GraphQuery.DataFlowPathNode;
import dev.dominikbreu.archlens.cache.GraphQuery.DataFlowSinkNode;
import dev.dominikbreu.archlens.cache.GraphQuery.EntrypointNode;
import dev.dominikbreu.archlens.cache.GraphQuery.GraphNode;
import dev.dominikbreu.archlens.cache.GraphQuery.RuntimeFlowStepNode;
import dev.dominikbreu.archlens.model.EntrypointType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Answers the {@code endpoint_context} intent: forward (what an endpoint does) and reverse (which endpoints reach a service). */
public final class EndpointContextAnswerer {

    private static final Set<EntrypointType> ENDPOINT_TYPES = Set.of(
            EntrypointType.REST_ENDPOINT,
            EntrypointType.SSE_ENDPOINT,
            EntrypointType.WEBSOCKET_ENDPOINT,
            EntrypointType.GRPC_METHOD);

    private EndpointContextAnswerer() {}

    /**
     * Answers a REST/use-case question, either forward from an entrypoint or reverse from a
     * component/service back to the endpoints that reach it.
     *
     * @param graph the graph to query
     * @param args {@code entrypoint} for forward mode, {@code component} for reverse mode, plus optional {@code maxDepth}
     * @param recorder the graph-operation recorder
     * @return the endpoint-context answer
     */
    public static Answer answer(GraphQuery graph, Map<String, Object> args, QueryPlanRecorder recorder) {
        String entrypointRef = QuestionSupport.first(args, "entrypoint", "entrypointId", "entrypointName");
        String componentRef = QuestionSupport.first(args, "component", "componentId");

        Answer probe = new Answer();
        EntrypointNode entrypoint =
                entrypointRef != null ? QuestionSupport.entrypoint(graph, entrypointRef, probe) : null;
        if (entrypoint != null) return forward(graph, entrypoint, recorder);

        Answer result = new Answer();
        if (componentRef == null) {
            if (entrypointRef != null) result.unresolved.addAll(probe.unresolved);
            result.ambiguous.addAll(probe.ambiguous);
            if (entrypointRef == null) result.unresolved.add("missing-subject: provide entrypoint or component");
            result.answer(emptyAnswer("forward"));
            return result;
        }
        ComponentNode component = QuestionSupport.component(graph, componentRef, result);
        if (component == null) {
            result.answer(emptyAnswer("reverse"));
            return result;
        }
        return reverse(graph, args, component, recorder);
    }

    private static Answer forward(GraphQuery graph, EntrypointNode entrypoint, QueryPlanRecorder recorder) {
        Answer result = new Answer();
        result.subject(QuestionSupport.nodeMap(entrypoint));
        recorder.record("resolveEntrypoint", "ref", entrypoint.id().serialize());

        Map<String, Object> inbound = new LinkedHashMap<>();
        inbound.put("httpMethod", entrypoint.httpMethod());
        inbound.put("path", entrypoint.path());
        inbound.put("parameters", entrypoint.parameters());

        Map<String, Object> owningComponent = Map.of();
        if (entrypoint.componentId() != null) {
            GraphNode component = graph.component(entrypoint.componentId());
            if (component != null) owningComponent = QuestionSupport.nodeMap(component);
        }

        List<Map<String, Object>> runtimeCalls = new ArrayList<>();
        List<Map<String, Object>> transactionTransitions = new ArrayList<>();
        graph.runtimeFlowForEntrypoint(entrypoint.id().serialize())
                .ifPresentOrElse(
                        flow -> {
                            recorder.record(
                                    "runtimeFlowForEntrypoint",
                                    "entrypoint",
                                    entrypoint.id().serialize());
                            List<RuntimeFlowStepNode> steps = graph.flowSteps(flow.id());
                            recorder.record("flowSteps", "flowId", flow.id().serialize());
                            for (RuntimeFlowStepNode step : steps) {
                                runtimeCalls.add(QuestionSupport.nodeMap(step));
                                if (step.transactionTransition() != null)
                                    transactionTransitions.add(QuestionSupport.nodeMap(step));
                            }
                        },
                        () -> result.unresolved.add("runtime-flow-not-resolved"));

        List<Map<String, Object>> dataFlowSinks = new ArrayList<>();
        List<Map<String, Object>> outboundCalls = new ArrayList<>();
        for (DataFlowPathNode path : graph.pathsForEntrypoint(entrypoint.id())) {
            for (DataFlowSinkNode sink : graph.pathSinks(path.id())) {
                Map<String, Object> sinkMap = QuestionSupport.nodeMap(sink);
                if (sink.sinkKind() != null
                        && "persistence".equals(sink.sinkKind().value())) {
                    dataFlowSinks.add(sinkMap);
                } else {
                    outboundCalls.add(sinkMap);
                }
            }
        }
        if (dataFlowSinks.isEmpty() && outboundCalls.isEmpty()) {
            result.unresolved.add("no-data-flow-sinks-resolved");
        }

        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("mode", "forward");
        answer.put("inbound", inbound);
        answer.put("owningComponent", owningComponent);
        answer.put("runtimeCalls", QuestionSupport.distinct(runtimeCalls));
        answer.put("dataFlowSinks", QuestionSupport.distinct(dataFlowSinks));
        answer.put("transactionTransitions", QuestionSupport.distinct(transactionTransitions));
        answer.put("outboundCalls", QuestionSupport.distinct(outboundCalls));
        answer.put("affectedEntrypoints", List.of());
        result.answer(answer);
        result.unresolved.add("security-not-modeled");
        result.unresolved.add("response-schema-not-modeled");
        return result;
    }

    private static Answer reverse(
            GraphQuery graph, Map<String, Object> args, ComponentNode component, QueryPlanRecorder recorder) {
        Answer result = new Answer();
        result.subject(QuestionSupport.nodeMap(component));
        int depth = QuestionSupport.integer(args, "maxDepth", 4);
        List<GraphNode> impacted = graph.impactedBy(component.id(), depth, QuestionSupport.DEFAULT_LIMIT);
        recorder.record(
                "impactedBy", Map.of("component", component.id().serialize(), "maxDepth", String.valueOf(depth)));

        List<Map<String, Object>> affectedEntrypoints = new ArrayList<>();
        List<Map<String, Object>> otherEntrypoints = new ArrayList<>();
        for (GraphNode node : impacted) {
            if (!(node instanceof EntrypointNode entrypointNode)) continue;
            if (ENDPOINT_TYPES.contains(entrypointNode.type())) {
                affectedEntrypoints.add(QuestionSupport.nodeMap(entrypointNode));
            } else {
                otherEntrypoints.add(QuestionSupport.nodeMap(entrypointNode));
            }
        }

        List<Map<String, Object>> evidenceChains = new ArrayList<>();
        for (GraphNode node : impacted) {
            if (!(node instanceof EntrypointNode)) continue;
            graph.paths(node.id(), component.id(), depth, 1).stream()
                    .map(QuestionSupport::pathMap)
                    .forEach(evidenceChains::add);
        }
        result.evidenceChain.addAll(evidenceChains);

        if (affectedEntrypoints.isEmpty() && !otherEntrypoints.isEmpty()) {
            result.unresolved.add("no-rest-typed-entrypoint-reaches-subject; non-rest callers found");
        } else if (affectedEntrypoints.isEmpty()) {
            result.unresolved.add("no-entrypoint-reaches-subject");
        }

        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("mode", "reverse");
        answer.put("inbound", Map.of());
        answer.put("owningComponent", Map.of());
        answer.put("runtimeCalls", List.of());
        answer.put("dataFlowSinks", List.of());
        answer.put("transactionTransitions", List.of());
        answer.put("outboundCalls", List.of());
        answer.put("affectedEntrypoints", QuestionSupport.distinct(affectedEntrypoints));
        answer.put("otherEntrypoints", QuestionSupport.distinct(otherEntrypoints));
        result.answer(answer);
        return result;
    }

    private static Map<String, Object> emptyAnswer(String mode) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("mode", mode);
        answer.put("inbound", Map.of());
        answer.put("owningComponent", Map.of());
        answer.put("runtimeCalls", List.of());
        answer.put("dataFlowSinks", List.of());
        answer.put("transactionTransitions", List.of());
        answer.put("outboundCalls", List.of());
        answer.put("affectedEntrypoints", List.of());
        return answer;
    }
}
