package dev.dominikbreu.archlens.renderer;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.model.ids.AppId;
import dev.dominikbreu.archlens.model.ids.GraphNodeId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * Renders deterministic Mermaid flowchart diagrams from the architecture graph.
 * Supports four levels: system, container, module, component (default).
 */
public class MermaidFlowchartRenderer {

    private static final String TECHNICAL_LIBRARY = "technical_library";
    private static final String INDENT8 = "        ";

    private final MermaidDialect dialect;

    /** Creates a renderer emitting universal Mermaid syntax. */
    public MermaidFlowchartRenderer() {
        this(MermaidDialect.UNIVERSAL);
    }

    /**
     * Creates a renderer for the given dialect.
     *
     * @param dialect output dialect; C4 affects system and container levels only
     */
    public MermaidFlowchartRenderer(MermaidDialect dialect) {
        this.dialect = dialect;
    }

    /**
     * Renders a Mermaid architecture flowchart.
     *
     * @param graph the graph to render
     * @param appIdFilter restrict to a single application id, or {@code null} for all
     * @param level the aggregation level to render at
     * @return the Mermaid diagram source
     */
    public String render(GraphQuery graph, String appIdFilter, String level) {
        String lvl = level != null ? level.toLowerCase() : "component";

        List<GraphQuery.ApplicationNode> apps = graph.allApplicationNodes().stream()
                .filter(a -> appIdFilter == null
                        || a.id().value().contains(appIdFilter)
                        || a.name().contains(appIdFilter))
                .toList();

        return switch (lvl) {
            case "system" ->
                dialect == MermaidDialect.C4 ? renderSystemC4(apps, graph) : renderSystemLevel(apps, graph);
            case "container" ->
                dialect == MermaidDialect.C4 ? renderContainerC4(apps, graph) : renderContainerLevel(apps, graph);
            case "module" -> renderModuleLevel(apps, graph);
            default -> renderComponentLevel(apps, graph);
        };
    }

    // ── module level ──────────────────────────────────────────────────────────

    private String renderModuleLevel(List<GraphQuery.ApplicationNode> apps, GraphQuery graph) {
        List<MermaidDocument.Statement> statements = new ArrayList<>();
        MermaidStyle.Tracker tracker = new MermaidStyle.Tracker();
        for (GraphQuery.ApplicationNode app : apps) {
            appendModuleApp(statements, app, graph, tracker);
        }
        appendCrossModuleDeps(statements, apps, graph);
        return MermaidTemplateAdapters.flowchart("TD", statements, tracker);
    }

    private void appendModuleApp(
            List<MermaidDocument.Statement> statements,
            GraphQuery.ApplicationNode app,
            GraphQuery graph,
            MermaidStyle.Tracker tracker) {
        if ("internal_module".equals(app.role()) || TECHNICAL_LIBRARY.equals(app.role())) return;

        List<GraphQuery.ApplicationNode> children =
                graph.childApps(AppId.of(app.id().value()));

        if (children.isEmpty()) {
            String label = app.name() + "\n" + app.packagingType()
                    + (app.technology() != null ? " / " + app.technology() : "");
            String id = nid(app.id().value());
            statements.add(
                    MermaidDocument.Statement.node(tracker.node("    ", id, label, MermaidStyle.Role.CONTAINER)));
        } else {
            statements.add(MermaidDocument.Statement.subgraph(
                    "    ", nid(app.id().value()), escape(app.name()) + " (" + app.packagingType() + ")"));
            for (GraphQuery.ApplicationNode child : children) {
                MermaidStyle.Role role = TECHNICAL_LIBRARY.equals(child.role())
                        ? MermaidStyle.Role.COMPONENT
                        : MermaidStyle.Role.CONTAINER;
                String label = child.name() + "\n" + child.role();
                String id = nid(child.id().value());
                statements.add(MermaidDocument.Statement.node(tracker.node(INDENT8, id, label, role)));
            }
            statements.add(MermaidDocument.Statement.end("    "));
        }
    }

    private void appendCrossModuleDeps(
            List<MermaidDocument.Statement> statements, List<GraphQuery.ApplicationNode> apps, GraphQuery graph) {
        Map<String, String> compToApp = buildCompToAppMap(apps, graph);
        Set<String> drawn = new HashSet<>();
        for (GraphQuery.GraphEdge dep : graph.dependencyEdges()) {
            String fromApp = compToApp.get(dep.fromId().value());
            String toApp = compToApp.get(dep.toId().value());
            if (fromApp == null || toApp == null || fromApp.equals(toApp)) continue;
            String key = fromApp + "->" + toApp;
            if (drawn.add(key)) {
                statements.add(MermaidDocument.Statement.edge(
                        new MermaidDocument.Edge("    ", nid(fromApp), nid(toApp), "", false, false, false, false)));
            }
        }
    }

    // ── system level ──────────────────────────────────────────────────────────

    private String renderSystemLevel(List<GraphQuery.ApplicationNode> apps, GraphQuery graph) {
        List<MermaidDocument.Statement> statements = new ArrayList<>();
        MermaidStyle.Tracker tracker = new MermaidStyle.Tracker();
        for (GraphQuery.ApplicationNode app : apps) {
            String label = app.name() + "\n" + app.technology() + " / " + app.packagingType();
            String id = nid(app.id().value());
            statements.add(
                    MermaidDocument.Statement.node(tracker.node("    ", id, label, MermaidStyle.Role.CONTAINER)));
        }

        Set<String> visibleApps = apps.stream().map(a -> a.id().value()).collect(Collectors.toSet());
        Map<String, String> compToApp = buildCompToAppMap(apps, graph);
        Set<String> referencedExternals = new LinkedHashSet<>();
        Set<String> drawnEdges = new LinkedHashSet<>();

        for (GraphQuery.GraphEdge dep : graph.dependencyEdges()) {
            if (!graph.isExternalSystem(dep.toId())) continue;
            String fromApp = compToApp.get(dep.fromId().value());
            if (fromApp == null || !visibleApps.contains(fromApp)) continue;
            referencedExternals.add(dep.toId().value());
            String kind = dep.properties().get("kind") instanceof String s ? s : "";
            String key = fromApp + "->" + dep.toId().value() + ":" + kind;
            if (drawnEdges.add(key)) {
                statements.add(MermaidDocument.Statement.edge(new MermaidDocument.Edge(
                        "    ",
                        nid(fromApp),
                        nid(dep.toId().value()),
                        escape(kind),
                        true,
                        false,
                        MermaidStyle.isAsyncKind(kind),
                        false)));
            }
        }

        for (GraphQuery.ExternalSystemNode ext : graph.allExternalSystemNodes()) {
            if (!referencedExternals.contains(ext.id().value())) continue;
            MermaidStyle.Role role = MermaidStyle.roleForExternalKind(ext.kind());
            String kindLabel = ext.kind() != null ? ext.kind().toUpperCase(Locale.ROOT) : "";
            String id = nid(ext.id().value());
            statements.add(
                    MermaidDocument.Statement.node(tracker.node("    ", id, ext.name() + "\n" + kindLabel, role)));
        }
        return MermaidTemplateAdapters.flowchart("TD", statements, tracker);
    }

    // ── container level ──────────────────────────────────────────────────────

    private String renderContainerLevel(List<GraphQuery.ApplicationNode> apps, GraphQuery graph) {
        List<MermaidDocument.Statement> statements = new ArrayList<>();
        MermaidStyle.Tracker tracker = new MermaidStyle.Tracker();

        Map<String, String> compToContainer = buildCompToContainerMap(apps, graph);
        Map<String, Long> epByContainer = graph.entrypointCountPerContainer();

        Set<String> visibleContainers = new LinkedHashSet<>();
        for (GraphQuery.ApplicationNode app : apps) {
            appendContainerSubgraph(statements, app, graph, epByContainer, visibleContainers, tracker);
        }

        Set<String> referencedExternals = new LinkedHashSet<>();
        Map<String, Set<String>> edgeKinds =
                aggregateContainerEdges(graph, compToContainer, visibleContainers, referencedExternals);
        appendExternalNodes(statements, graph, referencedExternals, tracker);
        appendLabelledEdges(statements, edgeKinds);
        return MermaidTemplateAdapters.flowchart("TD", statements, tracker);
    }

    private void appendContainerSubgraph(
            List<MermaidDocument.Statement> statements,
            GraphQuery.ApplicationNode app,
            GraphQuery graph,
            Map<String, Long> epByContainer,
            Set<String> visibleContainers,
            MermaidStyle.Tracker tracker) {
        statements.add(MermaidDocument.Statement.subgraph(
                "    ", nid(app.id().value()), escape(app.name()) + " (" + app.technology() + ")"));

        for (GraphQuery.ContainerNode container :
                graph.containersForApp(AppId.of(app.id().value()))) {
            visibleContainers.add(container.id().value());
            int compCount = graph.componentIdsInContainer(container.id()).size();
            long epCount = epByContainer.getOrDefault(container.id().value(), 0L);
            String label = container.name() + "\n" + compCount + " component" + (compCount != 1 ? "s" : "")
                    + (epCount > 0 ? " / " + epCount + " EP" : "");
            String id = nid(container.id().value());
            statements.add(
                    MermaidDocument.Statement.node(tracker.node(INDENT8, id, label, MermaidStyle.Role.CONTAINER)));
        }
        statements.add(MermaidDocument.Statement.end("    "));
    }

    private Map<String, Set<String>> aggregateContainerEdges(
            GraphQuery graph,
            Map<String, String> compToContainer,
            Set<String> visibleContainers,
            Set<String> referencedExternals) {
        Map<String, Set<String>> edgeKinds = new LinkedHashMap<>();
        for (GraphQuery.GraphEdge dep : graph.dependencyEdges()) {
            String fromC = compToContainer.get(dep.fromId().value());
            String kind = dep.properties().get("kind") instanceof String s ? s : "";

            if (fromC != null && visibleContainers.contains(fromC) && graph.isExternalSystem(dep.toId())) {
                referencedExternals.add(dep.toId().value());
                edgeKinds
                        .computeIfAbsent(fromC + "\0" + dep.toId().value(), k -> new LinkedHashSet<>())
                        .add(nullToEmpty(kind));
                continue;
            }

            String toC = compToContainer.get(dep.toId().value());
            if (fromC == null || toC == null || fromC.equals(toC)) continue;
            if (!visibleContainers.contains(fromC) || !visibleContainers.contains(toC)) continue;
            edgeKinds
                    .computeIfAbsent(fromC + "\0" + toC, k -> new LinkedHashSet<>())
                    .add(nullToEmpty(kind));
        }
        return edgeKinds;
    }

    private void appendExternalNodes(
            List<MermaidDocument.Statement> statements,
            GraphQuery graph,
            Set<String> referencedExternals,
            MermaidStyle.Tracker tracker) {
        for (GraphQuery.ExternalSystemNode ext : graph.allExternalSystemNodes()) {
            if (!referencedExternals.contains(ext.id().value())) continue;
            MermaidStyle.Role role = MermaidStyle.roleForExternalKind(ext.kind());
            String kindLabel = ext.kind() != null ? ext.kind().toUpperCase(Locale.ROOT) : "";
            String id = nid(ext.id().value());
            statements.add(
                    MermaidDocument.Statement.node(tracker.node("    ", id, ext.name() + "\n" + kindLabel, role)));
        }
    }

    private void appendLabelledEdges(List<MermaidDocument.Statement> statements, Map<String, Set<String>> edgeKinds) {
        for (Map.Entry<String, Set<String>> entry : edgeKinds.entrySet()) {
            String[] parts = entry.getKey().split("\0", 2);
            Set<String> kinds = entry.getValue();
            String kindLabel = String.join(", ", kinds);
            boolean allAsync = kinds.stream().allMatch(MermaidStyle::isAsyncKind);
            statements.add(MermaidDocument.Statement.edge(new MermaidDocument.Edge(
                    "    ", nid(parts[0]), nid(parts[1]), escape(kindLabel), true, false, allAsync, false)));
        }
    }

    // ── component level ──────────────────────────────────────────────────────

    private String renderComponentLevel(List<GraphQuery.ApplicationNode> apps, GraphQuery graph) {
        List<MermaidDocument.Statement> statements = new ArrayList<>();
        MermaidStyle.Tracker tracker = new MermaidStyle.Tracker();
        for (GraphQuery.ApplicationNode app : apps) {
            appendComponentSubgraph(statements, app, graph, tracker);
        }
        appendComponentEdges(statements, apps, graph);
        return MermaidTemplateAdapters.flowchart("TD", statements, tracker);
    }

    private void appendComponentSubgraph(
            List<MermaidDocument.Statement> statements,
            GraphQuery.ApplicationNode app,
            GraphQuery graph,
            MermaidStyle.Tracker tracker) {
        statements.add(MermaidDocument.Statement.subgraph(
                "    ", nid(app.id().value()), escape(app.name()) + " (" + app.technology() + ")"));

        List<GraphQuery.ContainerNode> appContainers =
                graph.containersForApp(AppId.of(app.id().value()));
        List<GraphQuery.ComponentNode> allComponents = graph.allComponentNodes();
        Map<GraphNodeId, GraphQuery.ComponentNode> compById = new LinkedHashMap<>();
        for (GraphQuery.ComponentNode c : allComponents) compById.put(c.id(), c);

        if (appContainers.isEmpty()) {
            for (GraphNodeId cid : graph.componentIdsOwnedBy(app.id())) {
                renderComponentNode(statements, cid, compById, INDENT8, tracker);
            }
        } else {
            for (GraphQuery.ContainerNode container : appContainers) {
                statements.add(MermaidDocument.Statement.subgraph(
                        "        ", nid(container.id().value()), escape(container.name())));
                for (GraphNodeId cid : graph.componentIdsInContainer(container.id())) {
                    renderComponentNode(statements, cid, compById, "            ", tracker);
                }
                statements.add(MermaidDocument.Statement.end("        "));
            }
        }
        statements.add(MermaidDocument.Statement.end("    "));
    }

    private void appendComponentEdges(
            List<MermaidDocument.Statement> statements, List<GraphQuery.ApplicationNode> apps, GraphQuery graph) {
        Set<String> visibleComps = apps.stream()
                .flatMap(a -> graph.componentIdsOwnedBy(a.id()).stream())
                .map(GraphNodeId::value)
                .collect(Collectors.toSet());

        for (GraphQuery.GraphEdge dep : graph.dependencyEdges()) {
            if (visibleComps.contains(dep.fromId().value())
                    && visibleComps.contains(dep.toId().value())) {
                String kind = dep.properties().get("kind") instanceof String s ? s : "";
                statements.add(MermaidDocument.Statement.edge(new MermaidDocument.Edge(
                        "    ",
                        nid(dep.fromId().value()),
                        nid(dep.toId().value()),
                        escape(kind),
                        true,
                        false,
                        MermaidStyle.isAsyncKind(kind),
                        false)));
            }
        }
    }

    private void renderComponentNode(
            List<MermaidDocument.Statement> statements,
            GraphNodeId cid,
            Map<GraphNodeId, GraphQuery.ComponentNode> byId,
            String indent,
            MermaidStyle.Tracker tracker) {
        GraphQuery.ComponentNode comp = byId.get(cid);
        if (comp == null) return;
        MermaidStyle.Role role = MermaidStyle.roleFor(comp.type());
        String label = comp.name() + "\n«"
                + (comp.type() != null ? comp.type().name().toLowerCase(Locale.ROOT) : "component") + "»";
        statements.add(MermaidDocument.Statement.node(tracker.node(indent, nid(cid.value()), label, role)));
    }

    // ── C4 system level ────────────────────────────────────────────────────────

    private String renderSystemC4(List<GraphQuery.ApplicationNode> apps, GraphQuery graph) {
        List<MermaidDocument.C4Element> elements = new ArrayList<>();
        for (GraphQuery.ApplicationNode app : apps) {
            elements.add(MermaidDocument.C4Element.element(
                    "System",
                    nid(app.id().value()),
                    escape(app.name()),
                    escape(app.technology() + " / " + app.packagingType()),
                    "",
                    false,
                    "    "));
        }

        Set<String> visibleApps = apps.stream().map(a -> a.id().value()).collect(Collectors.toSet());
        Map<String, String> compToApp = buildCompToAppMap(apps, graph);
        Set<String> referencedExternals = new LinkedHashSet<>();
        Set<String> drawnEdges = new LinkedHashSet<>();
        List<MermaidDocument.Relation> relations = new ArrayList<>();

        for (GraphQuery.GraphEdge dep : graph.dependencyEdges()) {
            if (!graph.isExternalSystem(dep.toId())) continue;
            String fromApp = compToApp.get(dep.fromId().value());
            if (fromApp == null || !visibleApps.contains(fromApp)) continue;
            referencedExternals.add(dep.toId().value());
            String kind = dep.properties().get("kind") instanceof String s ? s : "";
            String key = fromApp + "->" + dep.toId().value() + ":" + kind;
            if (drawnEdges.add(key)) {
                relations.add(new MermaidDocument.Relation(
                        nid(fromApp), nid(dep.toId().value()), escape(kind)));
            }
        }

        for (GraphQuery.ExternalSystemNode ext : graph.allExternalSystemNodes()) {
            if (!referencedExternals.contains(ext.id().value())) continue;
            String macro =
                    switch (MermaidStyle.roleForExternalKind(ext.kind())) {
                        case MESSAGING -> "SystemQueue_Ext";
                        case STORE -> "SystemDb_Ext";
                        default -> "System_Ext";
                    };
            String kindLabel = ext.kind() != null ? ext.kind().toUpperCase(Locale.ROOT) : "";
            elements.add(MermaidDocument.C4Element.element(
                    macro, nid(ext.id().value()), escape(ext.name()), escape(kindLabel), "", false, "    "));
        }
        return MermaidTemplateAdapters.c4(true, false, elements, relations);
    }

    // ── C4 container level ─────────────────────────────────────────────────────

    private String renderContainerC4(List<GraphQuery.ApplicationNode> apps, GraphQuery graph) {
        List<MermaidDocument.C4Element> elements = new ArrayList<>();
        Map<String, String> compToContainer = buildCompToContainerMap(apps, graph);
        Map<String, Long> epByContainer = graph.entrypointCountPerContainer();
        Set<String> visibleContainers = new LinkedHashSet<>();

        for (GraphQuery.ApplicationNode app : apps) {
            elements.add(MermaidDocument.C4Element.boundary(nid(app.id().value()), escape(app.name())));
            for (GraphQuery.ContainerNode container :
                    graph.containersForApp(AppId.of(app.id().value()))) {
                visibleContainers.add(container.id().value());
                int compCount = graph.componentIdsInContainer(container.id()).size();
                long epCount = epByContainer.getOrDefault(container.id().value(), 0L);
                String desc = compCount + " component" + (compCount != 1 ? "s" : "")
                        + (epCount > 0 ? " / " + epCount + " EP" : "");
                elements.add(MermaidDocument.C4Element.element(
                        "Container",
                        nid(container.id().value()),
                        escape(container.name()),
                        escape(app.technology()),
                        escape(desc),
                        true,
                        "        "));
            }
            elements.add(MermaidDocument.C4Element.closingBoundary());
        }

        Set<String> referencedExternals = new LinkedHashSet<>();
        Map<String, Set<String>> edgeKinds =
                aggregateContainerEdges(graph, compToContainer, visibleContainers, referencedExternals);

        for (GraphQuery.ExternalSystemNode ext : graph.allExternalSystemNodes()) {
            if (!referencedExternals.contains(ext.id().value())) continue;
            String macro =
                    switch (MermaidStyle.roleForExternalKind(ext.kind())) {
                        case MESSAGING -> "ContainerQueue_Ext";
                        case STORE -> "ContainerDb_Ext";
                        default -> "System_Ext";
                    };
            String kindLabel = ext.kind() != null ? ext.kind().toUpperCase(Locale.ROOT) : "";
            elements.add(MermaidDocument.C4Element.element(
                    macro, nid(ext.id().value()), escape(ext.name()), escape(kindLabel), "", false, "    "));
        }

        List<MermaidDocument.Relation> relations = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : edgeKinds.entrySet()) {
            String[] parts = entry.getKey().split("\0", 2);
            relations.add(new MermaidDocument.Relation(
                    nid(parts[0]), nid(parts[1]), escape(String.join(", ", entry.getValue()))));
        }
        return MermaidTemplateAdapters.c4(false, true, elements, relations);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Map<String, String> buildCompToAppMap(List<GraphQuery.ApplicationNode> apps, GraphQuery graph) {
        Map<String, String> map = new HashMap<>();
        for (GraphQuery.ApplicationNode app : apps) {
            for (GraphNodeId cid : graph.componentIdsOwnedBy(app.id())) {
                map.put(cid.value(), app.id().value());
            }
        }
        return map;
    }

    private Map<String, String> buildCompToContainerMap(List<GraphQuery.ApplicationNode> apps, GraphQuery graph) {
        Map<String, String> map = new HashMap<>();
        for (GraphQuery.ApplicationNode app : apps) {
            for (GraphQuery.ContainerNode c :
                    graph.containersForApp(AppId.of(app.id().value()))) {
                for (GraphNodeId cid : graph.componentIdsInContainer(c.id())) {
                    map.put(cid.value(), c.id().value());
                }
            }
        }
        return map;
    }

    private String nullToEmpty(String s) {
        return StringUtils.isBlank(s) ? "uses" : s;
    }

    private String nid(String id) {
        return MermaidStyle.nid(id);
    }

    private String escape(String s) {
        return Mermaid.escapeLabel(s);
    }
}
