package dev.dominikbreu.spoonmcp.renderer;

import dev.dominikbreu.spoonmcp.model.*;
import java.util.List;

/**
 * Renders a Mermaid {@code gantt} chart from a list of runtime flows.
 *
 * <p>Each flow becomes a {@code section}; each step in the call chain becomes a task bar
 * positioned by its call depth (0-based). The entrypoint's own component is rendered as
 * {@code :active} so it renders highlighted in Mermaid. The x-axis represents call depth
 * (step 0, 1, 2, …), not wall-clock time.
 *
 * <p>Task labels use {@code ComponentName.methodName} from {@link RuntimeFlowStep#componentName}
 * and {@link RuntimeFlowStep#via}.
 */
public class MermaidUseCaseTimelineRenderer {

    /** Creates a use-case timeline renderer with default formatting. */
    public MermaidUseCaseTimelineRenderer() {}

    /**
     * Renders a Mermaid gantt chart for the given runtime flows.
     *
     * @param flows    ordered list of flows (one per use case section)
     * @param model    architecture model used to resolve entrypoint labels
     * @param maxDepth maximum steps rendered per section
     * @return Mermaid gantt chart text
     */
    public String render(List<RuntimeFlow> flows, ArchitectureModel model, int maxDepth) {
        if (flows.isEmpty()) {
            return "gantt\n    title Use Case Execution Order\n    note[no use cases found]\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("gantt\n");
        sb.append("    title Use Case Execution Order\n");
        sb.append("    dateFormat  X\n");
        sb.append("    axisFormat  step %s\n");

        for (RuntimeFlow flow : flows) {
            Entrypoint ep = findEntrypoint(flow, model);
            String sectionTitle = sectionLabel(ep, flow);
            sb.append("\n    section ").append(sanitizeSection(sectionTitle)).append("\n");

            List<RuntimeFlowStep> steps = flow.steps;
            int limit = Math.min(steps.size(), maxDepth);
            for (int i = 0; i < limit; i++) {
                RuntimeFlowStep step = steps.get(i);
                String taskLabel = taskLabel(step);
                String style = (i == 0) ? "active, " : "";
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

    private String sectionLabel(Entrypoint ep, RuntimeFlow flow) {
        String epId = flow.entrypointId != null ? flow.entrypointId.serialize() : "";
        if (ep == null) return epId;
        if (ep.httpMethod != null && ep.path != null) return ep.httpMethod + " " + ep.path;
        if (ep.channelName != null) return ep.channelName;
        if (ep.name != null) return ep.name;
        return epId;
    }

    private String taskLabel(RuntimeFlowStep step) {
        String via = (step.via != null && !step.via.isBlank()) ? step.via : "call";
        return step.componentName + "." + via;
    }

    private String sanitizeSection(String s) {
        if (s == null) return "unknown";
        // Mermaid section labels cannot contain colons
        return s.replace(":", " -");
    }

    private String pad(String s, int width) {
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }

    private Entrypoint findEntrypoint(RuntimeFlow flow, ArchitectureModel model) {
        if (flow.entrypointId == null) return null;
        return model.entrypoints.stream()
                .filter(e -> flow.entrypointId.equals(e.id))
                .findFirst()
                .orElse(null);
    }
}
