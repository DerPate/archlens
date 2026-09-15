package dev.dominikbreu.archlens.renderer;

import dev.dominikbreu.archlens.view.ArchitectureViewProjection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
        MermaidStyle.Tracker tracker = new MermaidStyle.Tracker();
        List<MermaidDocument.Statement> statements = new ArrayList<>();
        statements.add(MermaidDocument.Statement.subgraph("    ", "scope", escape(projection.title())));
        Map<String, String> ids = new LinkedHashMap<>();
        int index = 0;
        for (ArchitectureViewProjection.Node node : projection.nodes()) {
            String id = "n" + index++;
            ids.put(node.id(), id);
            MermaidStyle.Role role = roleForKind(node.kind());
            String kindLabel = node.kind() == null ? "" : node.kind();
            statements.add(MermaidDocument.Statement.node(
                    tracker.node("        ", id, node.title() + "\n[" + kindLabel + "]", role)));
        }

        statements.add(MermaidDocument.Statement.end("    "));
        statements.add(MermaidDocument.Statement.emptyLine());

        for (ArchitectureViewProjection.Edge edge : projection.edges()) {
            String source = ids.get(edge.sourceId());
            String target = ids.get(edge.targetId());
            if (source == null || target == null) {
                continue;
            }
            statements.add(MermaidDocument.Statement.edge(
                    MermaidDocument.Edge.labeled("    ", source, target, escape(edge.title()), false)));
        }

        return MermaidTemplateAdapters.flowchart("LR", statements, tracker, projection.warnings());
    }

    private static MermaidStyle.Role roleForKind(String kind) {
        if (kind == null) return MermaidStyle.Role.COMPONENT;
        return switch (kind.toLowerCase(java.util.Locale.ROOT)) {
            case "service" -> MermaidStyle.Role.SERVICE;
            case "repository" -> MermaidStyle.Role.REPOSITORY;
            case "entity" -> MermaidStyle.Role.ENTITY;
            case "container" -> MermaidStyle.Role.CONTAINER;
            case "rest_resource", "entrypoint" -> MermaidStyle.Role.ENTRYPOINT;
            default -> MermaidStyle.Role.COMPONENT;
        };
    }

    private static String escape(String value) {
        return Mermaid.escapeLabel(value);
    }
}
