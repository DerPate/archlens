package dev.dominikbreu.archlens.renderer;

import dev.dominikbreu.archlens.view.ArchitectureViewProjection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Renders an {@link ArchitectureViewProjection} as a Mermaid {@code flowchart LR} diagram. */
public final class ArchitectureViewMermaidRenderer {

    /** Creates a renderer with default settings. */
    public ArchitectureViewMermaidRenderer() {}

    /**
     * Renders the given projection as a Mermaid flowchart string.
     *
     * @param projection the projection to render
     * @return a Mermaid {@code flowchart LR} diagram string
     */
    public String render(ArchitectureViewProjection projection) {
        StringBuilder sb = new StringBuilder();
        sb.append("flowchart LR\n");
        sb.append("    subgraph scope[\"").append(escape(projection.title())).append("\"]\n");

        Map<String, String> ids = new LinkedHashMap<>();
        int index = 0;
        for (ArchitectureViewProjection.Node node : projection.nodes()) {
            String id = "n" + index++;
            ids.put(node.id(), id);
            sb.append("        ")
                    .append(id)
                    .append("[\"")
                    .append(escape(node.title()))
                    .append("<br/>[")
                    .append(escape(node.kind()))
                    .append("]\"]\n");
        }

        sb.append("    end\n\n");

        for (ArchitectureViewProjection.Edge edge : projection.edges()) {
            String source = ids.get(edge.sourceId());
            String target = ids.get(edge.targetId());
            if (source == null || target == null) {
                continue;
            }
            sb.append("    ")
                    .append(source)
                    .append(" -->|")
                    .append(escape(edge.title()))
                    .append("| ")
                    .append(target)
                    .append("\n");
        }

        if (!projection.warnings().isEmpty()) {
            sb.append("\n%% Warnings:\n");
            for (String warning : projection.warnings()) {
                sb.append("%% - ").append(warning).append("\n");
            }
        }

        return sb.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        } else {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
