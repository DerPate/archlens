package dev.dominikbreu.archlens.renderer;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.model.ComponentType;
import java.util.List;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;

/**
 * Renders use-case execution as a Mermaid {@code flowchart}: one left-to-right chain per use
 * case, each chain wrapped in a subgraph named after its entrypoint.
 *
 * <p>This was a {@code gantt} until 2026-08-16. Gantt is a duration primitive, and the data here
 * is ordinal — step 0, 1, 2 — so every bar was one slot wide, which on a chart of N steps is
 * {@code 1/N} of the width. Labels of 15-35 characters could not fit inside a bar that narrow, so
 * Mermaid pushed them outside and the trailing ones clipped off the canvas. Flowchart nodes size
 * themselves to their text, so the label always fits and depth reads as chain length instead.
 */
public class MermaidUseCaseTimelineRenderer {

    /** Creates a new use-case timeline renderer. */
    public MermaidUseCaseTimelineRenderer() {}

    /**
     * Renders a Mermaid flowchart of use-case execution order.
     *
     * @param flows the runtime flows to render, one chain each
     * @param graph the graph the flows belong to
     * @param maxDepth how many steps to show per flow before summarizing the rest
     * @return the Mermaid diagram source
     */
    public String render(List<GraphQuery.RuntimeFlowNode> flows, GraphQuery graph, int maxDepth) {
        if (flows.isEmpty()) {
            return MermaidStyle.header() + "flowchart LR\n    none[\"no use cases found\"]\n";
        }

        StringBuilder sb = new StringBuilder(MermaidStyle.header());
        sb.append("flowchart LR\n");
        MermaidStyle.Tracker tracker = new MermaidStyle.Tracker();

        int flowIndex = 0;
        for (GraphQuery.RuntimeFlowNode flow : flows) {
            GraphQuery.GraphNode epNode = flow.entrypointId() != null ? graph.entrypoint(flow.entrypointId()) : null;
            GraphQuery.EntrypointNode ep = epNode instanceof GraphQuery.EntrypointNode en ? en : null;
            String prefix = "uc" + flowIndex;

            sb.append("    subgraph ")
                    .append(prefix)
                    .append("[\"")
                    .append(Mermaid.escapeLabel(sectionLabel(ep, flow)))
                    .append("\"]\n");
            sb.append("        direction LR\n");

            List<GraphQuery.RuntimeFlowStepNode> steps = graph.flowSteps(flow.id());
            int limit = Math.min(steps.size(), maxDepth);
            String previousId = null;
            for (int i = 0; i < limit; i++) {
                GraphQuery.RuntimeFlowStepNode step = steps.get(i);
                String nodeId = prefix + "s" + i;
                MermaidStyle.Role role = roleFor(step, graph);
                sb.append(MermaidStyle.node("        ", nodeId, taskLabel(step, graph), role));
                tracker.tag(nodeId, role);
                if (previousId != null) {
                    sb.append("        ")
                            .append(previousId)
                            .append(" --> ")
                            .append(nodeId)
                            .append("\n");
                }
                previousId = nodeId;
            }
            if (steps.size() > limit) {
                String moreId = prefix + "more";
                int remaining = steps.size() - limit;
                sb.append("        ")
                        .append(moreId)
                        .append("[\"")
                        .append(remaining)
                        .append(remaining == 1 ? " more step\"]\n" : " more steps\"]\n");
                if (previousId != null) {
                    sb.append("        ")
                            .append(previousId)
                            .append(" --> ")
                            .append(moreId)
                            .append("\n");
                }
            }
            sb.append("    end\n");
            flowIndex++;
        }

        sb.append(tracker.footer());
        return sb.toString();
    }

    private MermaidStyle.Role roleFor(GraphQuery.RuntimeFlowStepNode step, GraphQuery graph) {
        ComponentType type = null;
        if (step.componentId() != null
                && graph.component(step.componentId()) instanceof GraphQuery.ComponentNode cn
                && cn.type() != null) {
            type = cn.type();
        }
        if (type == null && step.componentType() != null) {
            try {
                type = ComponentType.valueOf(step.componentType().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException _) {
                type = null;
            }
        }
        return type == null ? MermaidStyle.Role.COMPONENT : MermaidStyle.roleFor(type);
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
}
