package dev.dominikbreu.archlens.mcp.tools.question;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.GraphQuery.DataFlowSinkNode;
import dev.dominikbreu.archlens.cache.GraphQuery.EntrypointNode;
import dev.dominikbreu.archlens.cache.GraphQuery.GraphEdge;
import dev.dominikbreu.archlens.cache.GraphQuery.GraphNode;
import dev.dominikbreu.archlens.cache.GraphQuery.RuntimeFlowStepNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Answers the {@code scheduled_workflow} intent: trigger evidence, call flow, state reads/writes, and sinks for a scheduled job. */
public final class ScheduledWorkflowAnswerer {

    private ScheduledWorkflowAnswerer() {}

    /**
     * Answers what a scheduled job triggers.
     *
     * @param graph the graph to query
     * @param args the {@code entrypoint} selector (a SCHEDULER entrypoint)
     * @param recorder the graph-operation recorder
     * @return the scheduled-workflow answer
     */
    public static Answer answer(GraphQuery graph, Map<String, Object> args, QueryPlanRecorder recorder) {
        Answer result = new Answer();
        String ref = QuestionSupport.first(args, "entrypoint", "entrypointId", "entrypointName");
        if (ref == null) {
            result.unresolved.add("missing-subject: provide entrypoint");
            result.answer(emptyAnswer());
            return result;
        }
        EntrypointNode entrypoint = QuestionSupport.entrypoint(graph, ref, result);
        if (entrypoint == null) {
            result.answer(emptyAnswer());
            return result;
        }
        result.subject(QuestionSupport.nodeMap(entrypoint));

        Map<String, Object> triggerEvidence = new LinkedHashMap<>();
        triggerEvidence.put("kind", entrypoint.properties().get("triggerKind"));
        triggerEvidence.put("expression", entrypoint.properties().get("triggerExpression"));
        if (entrypoint.properties().get("triggerKind") == null) {
            result.unresolved.add("trigger-schedule-not-modeled");
        }

        List<Map<String, Object>> runtimeCalls = new ArrayList<>();
        List<Map<String, Object>> stateReads = new ArrayList<>();
        List<Map<String, Object>> stateWrites = new ArrayList<>();
        recorder.record(
                "runtimeFlowForEntrypoint", "entrypoint", entrypoint.id().serialize());
        graph.runtimeFlowForEntrypoint(entrypoint.id().serialize())
                .ifPresentOrElse(
                        flow -> {
                            recorder.record("flowSteps", "flowId", flow.id().serialize());
                            for (RuntimeFlowStepNode step : graph.flowSteps(flow.id())) {
                                runtimeCalls.add(QuestionSupport.nodeMap(step));
                                if (step.componentId() == null) continue;
                                GraphNode component = graph.component(step.componentId());
                                if (component == null) continue;
                                for (GraphEdge edge :
                                        graph.neighborhood(component.id(), "out", QuestionSupport.DEFAULT_LIMIT)) {
                                    if ("READS_STATE".equals(edge.label())) stateReads.add(edge.properties());
                                    if ("WRITES_STATE".equals(edge.label())) stateWrites.add(edge.properties());
                                }
                            }
                        },
                        () -> result.unresolved.add("runtime-flow-not-resolved"));

        List<Map<String, Object>> messagingAndExternalSinks = new ArrayList<>();
        for (var path : graph.pathsForEntrypoint(entrypoint.id())) {
            for (DataFlowSinkNode sink : graph.pathSinks(path.id())) {
                if (sink.sinkKind() != null
                        && !"persistence".equals(sink.sinkKind().value())) {
                    messagingAndExternalSinks.add(QuestionSupport.nodeMap(sink));
                }
            }
        }

        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("triggerEvidence", triggerEvidence);
        answer.put("runtimeCalls", QuestionSupport.distinct(runtimeCalls));
        answer.put("stateReads", stateReads);
        answer.put("stateWrites", stateWrites);
        answer.put("messagingAndExternalSinks", QuestionSupport.distinct(messagingAndExternalSinks));
        result.answer(answer);
        return result;
    }

    private static Map<String, Object> emptyAnswer() {
        return Map.of(
                "triggerEvidence", Map.of(),
                "runtimeCalls", List.of(),
                "stateReads", List.of(),
                "stateWrites", List.of(),
                "messagingAndExternalSinks", List.of());
    }
}
