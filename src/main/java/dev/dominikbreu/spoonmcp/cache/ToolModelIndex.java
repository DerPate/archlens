package dev.dominikbreu.spoonmcp.cache;

import dev.dominikbreu.spoonmcp.extractor.ComponentIndex;
import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.model.ids.AppId;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * O(1) lookup index over a loaded {@link ArchitectureModel} for use by MCP tools.
 * Supplements the extractor-side {@link ComponentIndex} (already HashMap-backed) with
 * missing per-ID indices for entrypoints and applications.
 */
public final class ToolModelIndex {

    private final ComponentIndex componentIndex;
    private final Map<EntrypointId, Entrypoint> byEntrypointId;
    private final Map<AppId, AppEntry> byAppId;
    private final ArchitectureModel model;

    private ToolModelIndex(
            ComponentIndex componentIndex,
            Map<EntrypointId, Entrypoint> byEntrypointId,
            Map<AppId, AppEntry> byAppId,
            ArchitectureModel model) {
        this.componentIndex = componentIndex;
        this.byEntrypointId = byEntrypointId;
        this.byAppId = byAppId;
        this.model = model;
    }

    public static ToolModelIndex from(ArchitectureModel model) {
        if (model == null) {
            return new ToolModelIndex(
                    ComponentIndex.build(List.of()),
                    Map.of(),
                    Map.of(),
                    null);
        }
        Map<EntrypointId, Entrypoint> byEp = new HashMap<>();
        for (Entrypoint ep : model.entrypoints) {
            if (ep.id != null) byEp.put(ep.id, ep);
        }
        Map<AppId, AppEntry> byApp = new HashMap<>();
        for (AppEntry app : model.applications) {
            if (app.id != null) byApp.put(app.id, app);
        }
        return new ToolModelIndex(
                ComponentIndex.build(model.components),
                byEp,
                byApp,
                model);
    }

    /** O(1) lookup by typed ComponentId. */
    public Component component(ComponentId id) {
        return componentIndex.get(id);
    }

    /**
     * Resolves a component by serialized ID, qualified name, or simple name —
     * centralising the three-way match that was copy-pasted across every tool.
     */
    public Component component(String nameOrId) {
        if (nameOrId == null) return null;
        Component c = componentIndex.getByQualifiedName(nameOrId);
        if (c != null) return c;
        c = componentIndex.find(nameOrId, nameOrId);
        if (c != null) return c;
        // fallback: contains-match on qualified name
        for (Component candidate : model == null ? List.<Component>of() : model.components) {
            if (candidate.id != null && candidate.id.qualifiedName().contains(nameOrId)) {
                return candidate;
            }
        }
        return null;
    }

    /** O(1) lookup by typed EntrypointId. */
    public Entrypoint entrypoint(EntrypointId id) {
        return byEntrypointId.get(id);
    }

    /** O(1) lookup by typed AppId. */
    public AppEntry app(AppId id) {
        return byAppId.get(id);
    }

    public List<Entrypoint> allEntrypoints() {
        return model == null ? List.of() : model.entrypoints;
    }

    public List<AppEntry> allApps() {
        return model == null ? List.of() : model.applications;
    }

    public List<Container> containers() {
        return model == null ? List.of() : model.containers;
    }

    public List<RuntimeFlow> runtimeFlows() {
        return model == null ? List.of() : model.runtimeFlows;
    }

    /** Escape hatch for tools whose graph migration is deferred to v3. */
    public ArchitectureModel rawModel() {
        return model;
    }
}
