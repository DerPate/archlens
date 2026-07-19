package dev.dominikbreu.archlens.mcp.tools.question;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.GraphQuery.ComponentNode;
import dev.dominikbreu.archlens.cache.GraphQuery.EntrypointNode;
import dev.dominikbreu.archlens.cache.GraphQuery.GraphNode;
import dev.dominikbreu.archlens.cache.GraphQuery.RuntimeFlowNode;
import dev.dominikbreu.archlens.cache.GraphQuery.RuntimeFlowStepNode;
import dev.dominikbreu.archlens.mcp.tools.ToolArgs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Answers the {@code transaction_context} intent: effective policies, scope transitions, governed calls, and caveats. */
public final class TransactionContextAnswerer {

    private TransactionContextAnswerer() {}

    /**
     * Answers which transaction policy governs a repository call, and what scope it runs in.
     *
     * @param graph the graph to query
     * @param args {@code entrypoint} or {@code component} (with optional {@code method}) selectors
     * @param recorder the graph-operation recorder (currently unused by this answerer)
     * @return the transaction-context answer
     */
    public static Answer answer(GraphQuery graph, Map<String, Object> args, QueryPlanRecorder recorder) {
        Answer result = new Answer();
        String entrypointRef = QuestionSupport.first(args, "entrypoint", "entrypointId", "entrypointName");
        String componentRef = QuestionSupport.first(args, "component", "componentId");
        String method = ToolArgs.getString(args, "method");
        List<Map<String, Object>> policies = new ArrayList<>();
        List<Map<String, Object>> steps = new ArrayList<>();
        List<Map<String, Object>> governedCalls = new ArrayList<>();

        if (entrypointRef != null) {
            EntrypointNode entrypoint = QuestionSupport.entrypoint(graph, entrypointRef, result);
            if (entrypoint != null) {
                result.subject(QuestionSupport.nodeMap(entrypoint));
                RuntimeFlowNode flow = graph.runtimeFlowForEntrypoint(
                                entrypoint.id().serialize())
                        .orElse(null);
                if (flow != null) {
                    for (RuntimeFlowStepNode step : graph.flowSteps(flow.id())) {
                        steps.add(QuestionSupport.nodeMap(step));
                        if (step.componentId() == null || step.method() == null) continue;
                        Map<String, String> filters =
                                Map.of("componentId", step.componentId().serialize(), "methodName", step.method());
                        for (GraphNode boundary :
                                graph.findNodes("TransactionBoundary", null, filters, QuestionSupport.DEFAULT_LIMIT)) {
                            policies.add(QuestionSupport.nodeMap(boundary));
                            QuestionSupport.outgoingNodes(graph, boundary.id(), "GOVERNS_OPERATION").stream()
                                    .map(QuestionSupport::nodeMap)
                                    .forEach(governedCalls::add);
                        }
                    }
                } else {
                    result.unresolved.add("runtime-flow-not-resolved");
                }
            }
        } else if (componentRef != null) {
            ComponentNode component = QuestionSupport.component(graph, componentRef, result);
            if (component != null) {
                result.subject(QuestionSupport.nodeMap(component));
                Map<String, String> filters = new LinkedHashMap<>();
                filters.put("componentId", component.id().serialize());
                if (method != null) filters.put("methodName", method);
                for (GraphNode node :
                        graph.findNodes("TransactionBoundary", null, filters, QuestionSupport.DEFAULT_LIMIT)) {
                    policies.add(QuestionSupport.nodeMap(node));
                    QuestionSupport.outgoingNodes(graph, node.id(), "GOVERNS_OPERATION").stream()
                            .map(QuestionSupport::nodeMap)
                            .forEach(governedCalls::add);
                }
            }
        } else {
            result.unresolved.add("missing-entrypoint-or-component");
        }
        if (policies.isEmpty()) result.unresolved.add("no-effective-transaction-policy-resolved");
        List<String> caveats = new ArrayList<>();
        for (Map<String, Object> policy : policies) {
            Object limitations = QuestionSupport.nested(policy, "properties", "limitations");
            if (limitations != null) caveats.add(limitations.toString());
        }
        for (Map<String, Object> step : steps) {
            Object limitations = QuestionSupport.nested(step, "properties", "transactionLimitations");
            if (limitations != null) caveats.add(limitations.toString());
        }
        result.answer(Map.of(
                "policies", QuestionSupport.distinct(policies),
                "scopeTransitions", QuestionSupport.distinct(steps),
                "governedCalls", QuestionSupport.distinct(governedCalls),
                "caveats", caveats.stream().distinct().toList()));
        return result;
    }
}
