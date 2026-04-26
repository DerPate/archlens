package dev.dominikbreu.spoonmcp.renderer;

import dev.dominikbreu.spoonmcp.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Renders a Mermaid sequenceDiagram from a RuntimeFlow.
 * Supports three condensation levels:
 *   component (default) — one participant per component
 *   container           — participants are containers; intra-container calls collapsed
 *   system              — participants are applications; intra-app calls collapsed
 */
public class MermaidSequenceRenderer {

    /** Creates a sequence diagram renderer for runtime flows. */
    public MermaidSequenceRenderer() {}

    /**
     * Renders a component-level Mermaid sequence diagram.
     *
     * @param flow runtime flow to render
     * @param model architecture model containing component metadata
     * @return Mermaid sequence diagram text
     */
    public String render(RuntimeFlow flow, ArchitectureModel model) {
        return render(flow, model, "component");
    }

    /**
     * Renders a Mermaid sequence diagram at component, container, or system level.
     *
     * @param flow runtime flow to render
     * @param model architecture model containing component metadata
     * @param level component, container, or system
     * @return Mermaid sequence diagram text
     */
    public String render(RuntimeFlow flow, ArchitectureModel model, String level) {
        if (flow == null || flow.steps.isEmpty()) {
            return "sequenceDiagram\n    note over Client: no flow steps found\n";
        }

        String lvl = level != null ? level.toLowerCase() : "component";

        return switch (lvl) {
            case "system"    -> renderSystemLevel(flow, model);
            case "container" -> renderContainerLevel(flow, model);
            default          -> renderComponentLevel(flow, model);
        };
    }

    // ── component level (full detail) ────────────────────────────────────────

    private String renderComponentLevel(RuntimeFlow flow, ArchitectureModel model) {
        Entrypoint ep = findEntrypoint(flow, model);
        List<RuntimeFlowStep> steps = flow.steps;

        StringBuilder sb = new StringBuilder("sequenceDiagram\n");
        sb.append("    participant Client\n");
        for (RuntimeFlowStep step : steps) {
            String pid = pid(step.componentId);
            String label = step.componentType + " " + step.componentName;
            sb.append("    participant ").append(pid)
              .append(" as ").append(escape(label)).append("\n");
        }
        sb.append("\n");

        // Client → first
        if (!steps.isEmpty()) {
            String first = pid(steps.get(0).componentId);
            appendClientCall(sb, first, ep);
        }

        // Forward calls
        for (int i = 0; i < steps.size() - 1; i++) {
            sb.append("    ").append(pid(steps.get(i).componentId))
              .append("->>").append(pid(steps.get(i + 1).componentId))
              .append(": [").append(steps.get(i + 1).via).append("]\n");
        }

        // Return calls
        for (int i = steps.size() - 1; i > 0; i--) {
            sb.append("    ").append(pid(steps.get(i).componentId))
              .append("-->>").append(pid(steps.get(i - 1).componentId))
              .append(": result\n");
        }

        if (!steps.isEmpty()) {
            sb.append("    ").append(pid(steps.get(0).componentId)).append("->>Client: response\n");
        }

        return sb.toString();
    }

    // ── container level ───────────────────────────────────────────────────────

    private String renderContainerLevel(RuntimeFlow flow, ArchitectureModel model) {
        Entrypoint ep = findEntrypoint(flow, model);
        Map<String, String> compToContainer = buildCompToContainerMap(model);

        // Ordered deduplicated container sequence
        List<String> containerSequence = flow.steps.stream()
            .map(s -> compToContainer.getOrDefault(s.componentId, "unknown"))
            .distinct()
            .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder("sequenceDiagram\n");
        sb.append("    participant Client\n");
        for (String cid : containerSequence) {
            String label = containerLabel(cid, model);
            sb.append("    participant ").append(pid(cid))
              .append(" as ").append(escape(label)).append("\n");
        }
        sb.append("\n");

        if (!containerSequence.isEmpty()) {
            appendClientCall(sb, pid(containerSequence.get(0)), ep);
        }

        for (int i = 0; i < containerSequence.size() - 1; i++) {
            sb.append("    ").append(pid(containerSequence.get(i)))
              .append("->>").append(pid(containerSequence.get(i + 1))).append(": call\n");
        }

        for (int i = containerSequence.size() - 1; i > 0; i--) {
            sb.append("    ").append(pid(containerSequence.get(i)))
              .append("-->>").append(pid(containerSequence.get(i - 1))).append(": result\n");
        }

        if (!containerSequence.isEmpty()) {
            sb.append("    ").append(pid(containerSequence.get(0))).append("->>Client: response\n");
        }

        return sb.toString();
    }

    // ── system level ──────────────────────────────────────────────────────────

    private String renderSystemLevel(RuntimeFlow flow, ArchitectureModel model) {
        Entrypoint ep = findEntrypoint(flow, model);
        Map<String, String> compToApp = buildCompToAppMap(model);

        List<String> appSequence = flow.steps.stream()
            .map(s -> compToApp.getOrDefault(s.componentId, "unknown"))
            .distinct()
            .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder("sequenceDiagram\n");
        sb.append("    participant Client\n");
        for (String appId : appSequence) {
            String label = appLabel(appId, model);
            sb.append("    participant ").append(pid(appId))
              .append(" as ").append(escape(label)).append("\n");
        }
        sb.append("\n");

        if (!appSequence.isEmpty()) {
            appendClientCall(sb, pid(appSequence.get(0)), ep);
        }

        for (int i = 0; i < appSequence.size() - 1; i++) {
            sb.append("    ").append(pid(appSequence.get(i)))
              .append("->>").append(pid(appSequence.get(i + 1))).append(": call\n");
        }

        for (int i = appSequence.size() - 1; i > 0; i--) {
            sb.append("    ").append(pid(appSequence.get(i)))
              .append("-->>").append(pid(appSequence.get(i - 1))).append(": result\n");
        }

        if (!appSequence.isEmpty()) {
            sb.append("    ").append(pid(appSequence.get(0))).append("->>Client: response\n");
        }

        return sb.toString();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void appendClientCall(StringBuilder sb, String firstPid, Entrypoint ep) {
        if (ep != null && ep.httpMethod != null) {
            sb.append("    Client->>").append(firstPid)
              .append(": ").append(ep.httpMethod).append(" ").append(ep.path).append("\n");
        } else {
            sb.append("    Client->>").append(firstPid).append(": invoke\n");
        }
    }

    private Entrypoint findEntrypoint(RuntimeFlow flow, ArchitectureModel model) {
        return model.entrypoints.stream()
            .filter(e -> e.id.equals(flow.entrypointId))
            .findFirst().orElse(null);
    }

    private Map<String, String> buildCompToContainerMap(ArchitectureModel model) {
        Map<String, String> map = new HashMap<>();
        for (Container c : model.containers) {
            for (String cid : c.componentIds) map.put(cid, c.id);
        }
        return map;
    }

    private Map<String, String> buildCompToAppMap(ArchitectureModel model) {
        Map<String, String> map = new HashMap<>();
        for (AppEntry app : model.applications) {
            for (String cid : app.componentIds) map.put(cid, app.id);
        }
        return map;
    }

    private String containerLabel(String containerId, ArchitectureModel model) {
        return model.containers.stream()
            .filter(c -> c.id.equals(containerId))
            .findFirst()
            .map(c -> c.name)
            .orElse(containerId);
    }

    private String appLabel(String appId, ArchitectureModel model) {
        return model.applications.stream()
            .filter(a -> a.id.equals(appId))
            .findFirst()
            .map(a -> a.name)
            .orElse(appId);
    }

    private String pid(String id) {
        return id.replaceAll("[^a-zA-Z0-9]", "_");
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "'");
    }
}
