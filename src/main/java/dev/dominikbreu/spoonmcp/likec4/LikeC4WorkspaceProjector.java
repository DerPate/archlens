package dev.dominikbreu.spoonmcp.likec4;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.model.AppEntry;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.view.ArchitectureViewProjection;
import dev.dominikbreu.spoonmcp.view.ArchitectureViewProjector;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LikeC4WorkspaceProjector {

    private final ArchitectureViewProjector componentProjector;

    public LikeC4WorkspaceProjector() {
        this(new ArchitectureViewProjector());
    }

    LikeC4WorkspaceProjector(ArchitectureViewProjector componentProjector) {
        this.componentProjector = componentProjector;
    }

    public LikeC4Document projectWorkspace(
            ArchitectureGraph graph, ArchitectureModel model, AppEntry app, int maxNodes) {
        LikeC4Element system = systemElement(model, app);
        String scopeId;
        if (app != null) {
            scopeId = app.id.serialize();
        } else {
            scopeId = "";
        }
        String title = system.title() + " - Component View";
        ArchitectureViewProjection componentProjection =
                componentProjector.projectComponentView(graph, scopeId, title, maxNodes);

        List<LikeC4Element> components = componentProjection.nodes().stream()
                .map(node ->
                        new LikeC4Element(node.id(), "component", node.title(), node.id(), metadata(node.properties())))
                .toList();

        List<LikeC4Relationship> relationships = componentProjection.edges().stream()
                .map(edge -> new LikeC4Relationship(
                        edge.sourceId(), edge.targetId(), edge.title(), edge.label(), Map.of("label", edge.label())))
                .toList();

        List<LikeC4Element> elements = new ArrayList<>();
        elements.add(system);
        elements.addAll(components);

        List<String> systemIds = List.of(system.id());
        List<String> componentIds = components.stream().map(LikeC4Element::id).toList();
        List<String> systemAndComponentIds = new ArrayList<>();
        systemAndComponentIds.add(system.id());
        systemAndComponentIds.addAll(componentIds);

        List<String> warnings = new ArrayList<>(componentProjection.warnings());
        if (relationships.isEmpty()) {
            warnings.add("No LikeC4 relationships found between selected components");
        }

        return new LikeC4Document(
                List.of("system", "component"),
                elements,
                relationships,
                List.of(
                        new LikeC4View("context", "Context", systemIds, List.of()),
                        new LikeC4View("container", "Container", systemAndComponentIds, List.of()),
                        new LikeC4View("component", "Component", componentIds, List.of())),
                warnings);
    }

    private static LikeC4Element systemElement(ArchitectureModel model, AppEntry app) {
        if (app != null) {
            return new LikeC4Element(
                    app.id.serialize(),
                    "system",
                    title(app.name, app.id.serialize()),
                    app.id.serialize(),
                    appMetadata(app));
        }
        String workspace;
        if (model != null) {
            workspace = model.workspacePath;
        } else {
            workspace = null;
        }
        return new LikeC4Element(
                "system:workspace",
                "system",
                title(workspace, "Workspace"),
                "workspace",
                workspace == null || workspace.isBlank() ? Map.of() : Map.of("workspacePath", workspace));
    }

    private static Map<String, Object> appMetadata(AppEntry app) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "technology", app.technology);
        putIfPresent(metadata, "packagingType", app.packagingType);
        putIfPresent(metadata, "role", app.role);
        putIfPresent(metadata, "rootPath", app.rootPath);
        return metadata;
    }

    private static Map<String, Object> metadata(Map<String, Object> properties) {
        if (properties == null) {
            return Map.of();
        } else {
            return properties;
        }
    }

    private static void putIfPresent(Map<String, Object> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }

    private static String title(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        } else {
            return value;
        }
    }
}
