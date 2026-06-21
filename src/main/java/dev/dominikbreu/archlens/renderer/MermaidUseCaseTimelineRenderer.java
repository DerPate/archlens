package dev.dominikbreu.archlens.renderer;

import dev.dominikbreu.archlens.cache.GraphQuery;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * Renders a Mermaid {@code gantt} chart from a list of runtime flows.
 */
public class MermaidUseCaseTimelineRenderer {

    public MermaidUseCaseTimelineRenderer() {}

    public String render(List<GraphQuery.RuntimeFlowNode> flows, GraphQuery graph, int maxDepth) {
        if (flows.isEmpty()) {
            return "gantt\n    title Use Case Execution Order\n    note[no use cases found]\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("gantt\n");
        sb.append("    title Use Case Execution Order\n");
        sb.append("    dateFormat  X\n");
        sb.append("    axisFormat  step %s\n");

        for (GraphQuery.RuntimeFlowNode flow : flows) {
            GraphQuery.GraphNode epNode = flow.entrypointId() != null ? graph.entrypoint(flow.entrypointId()) : null;
            GraphQuery.EntrypointNode ep = epNode instanceof GraphQuery.EntrypointNode en ? en : null;
            String sectionTitle = sectionLabel(ep, flow);
            sb.append("\n    section ").append(sanitizeSection(sectionTitle)).append("\n");

            List<GraphQuery.RuntimeFlowStepNode> steps = graph.flowSteps(flow.id());
            int limit = Math.min(steps.size(), maxDepth);
            for (int i = 0; i < limit; i++) {
                GraphQuery.RuntimeFlowStepNode step = steps.get(i);
                String taskLabel = taskLabel(step, graph);
                String style = i == 0 ? "active, " : "";
                sb.append("    ")
                        .append(pad(taskLabel, 36))
                        .append(":")
                        .append(style)
                        .append(i)
                        .append(", 1\n");
            }
            if (steps.size() > limit) {
                sb.append("    ... (")
                        .append(steps.size() - limit)
                        .append(" more steps) :crit, ")
                        .append(limit)
                        .append(", 1\n");
            }
        }
        return sb.toString();
    }

    private String sectionLabel(GraphQuery.EntrypointNode ep, GraphQuery.RuntimeFlowNode flow) {
        String epId = flow.entrypointId() != null ? flow.entrypointId().serialize() : "";
        if (ep == null) return epId;
        if (ep.httpMethod() != null && ep.path() != null) return ep.httpMethod() + " " + ep.path();
        if (ep.channelName() != null) return ep.channelName();
        if (ep.name() != null) return ep.name();
        return epId;
    }

    private String taskLabel(GraphQuery.RuntimeFlowStepNode step, GraphQuery graph) {
        String compName = null;
        if (step.componentId() != null) {
            GraphQuery.GraphNode compNode = graph.component(step.componentId());
            if (compNode instanceof GraphQuery.ComponentNode cn) compName = cn.name();
        }
        if (compName == null) {
            compName = step.componentId() != null ? step.componentId().serialize() : "?";
        }
        String via = StringUtils.isNotBlank(step.via()) ? step.via() : "call";
        return compName + "." + via;
    }

    private String sanitizeSection(String s) {
        if (s == null) return "unknown";
        return s.replace(":", " -");
    }

    private String pad(String s, int width) {
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }
}
