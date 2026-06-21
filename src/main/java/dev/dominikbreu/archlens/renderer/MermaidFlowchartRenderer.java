package dev.dominikbreu.archlens.renderer;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.model.ids.AppId;
import dev.dominikbreu.archlens.model.ids.GraphNodeId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * Renders deterministic Mermaid flowchart diagrams from the architecture graph.
 * Supports four levels: system, container, module, component (default).
 */
public class MermaidFlowchartRenderer {

    private static final String FLOWCHART_HEADER = "flowchart TD\n";
    private static final String TECHNICAL_LIBRARY = "technical_library";
    private static final String SUBGRAPH_OPEN = "    subgraph ";
    private static final String SUBGRAPH_CLOSE = "    end\n";
    private static final String INDENT8 = "        ";
    private static final String EDGE_LABEL_OPEN = " -->|";
    private static final String NODE_CLOSE_PAREN = ")\"]\n";
    private static final String NODE_CLOSE = "\"]\n";

    public MermaidFlowchartRenderer() {}

    public String render(GraphQuery graph, String appIdFilter, String level) {
        String lvl = level != null ? level.toLowerCase() : "component";

        List<GraphQuery.ApplicationNode> apps = graph.allApplicationNodes().stream()
                .filter(a -> appIdFilter == null
                        || a.id().value().contains(appIdFilter)
                        || a.name().contains(appIdFilter))
                .toList();

        return switch (lvl) {
            case "system" -> renderSystemLevel(apps, graph);
            case "container" -> renderContainerLevel(apps, graph);
            case "module" -> renderModuleLevel(apps, graph);
            default -> renderComponentLevel(apps, graph);
        };
    }

    // ── module level ──────────────────────────────────────────────────────────

    private String renderModuleLevel(List<GraphQuery.ApplicationNode> apps, GraphQuery graph) {
        StringBuilder sb = new StringBuilder(FLOWCHART_HEADER);
        for (GraphQuery.ApplicationNode app : apps) {
            appendModuleApp(sb, app, graph);
        }
        appendCrossModuleDeps(sb, apps, graph);
        return sb.toString();
    }

    private void appendModuleApp(StringBuilder sb, GraphQuery.ApplicationNode app, GraphQuery graph) {
        if ("internal_module".equals(app.role()) || TECHNICAL_LIBRARY.equals(app.role())) return;

        List<GraphQuery.ApplicationNode> children = graph.childApps(AppId.of(app.id().value()));

        if (children.isEmpty()) {
            String label = app.name() + "\\n" + app.packagingType()
                    + (app.technology() != null ? " / " + app.technology() : "");
            sb.append("    ").append(nid(app.id().value())).append("[\"")
                    .append(escape(label)).append(NODE_CLOSE);
        } else {
            sb.append(SUBGRAPH_OPEN).append(nid(app.id().value())).append("[\"")
                    .append(escape(app.name())).append(" (").append(app.packagingType())
                    .append(NODE_CLOSE_PAREN);
            for (GraphQuery.ApplicationNode child : children) {
                String shape = TECHNICAL_LIBRARY.equals(child.role()) ? "([" : "[";
                String closeShape = TECHNICAL_LIBRARY.equals(child.role()) ? "])" : "]";
                String label = child.name() + "\\n" + child.role();
                sb.append(INDENT8).append(nid(child.id().value()))
                        .append(shape).append("\"").append(escape(label)).append("\"")
                        .append(closeShape).append("\n");
            }
            sb.append(SUBGRAPH_CLOSE);
        }
    }

    private void appendCrossModuleDeps(
            StringBuilder sb, List<GraphQuery.ApplicationNode> apps, GraphQuery graph) {
        Map<String, String> compToApp = buildCompToAppMap(apps, graph);
        Set<String> drawn = new HashSet<>();
        for (GraphQuery.GraphEdge dep : graph.dependencyEdges()) {
            String fromApp = compToApp.get(dep.fromId().value());
            String toApp = compToApp.get(dep.toId().value());
            if (fromApp == null || toApp == null || fromApp.equals(toApp)) continue;
            String key = fromApp + "->" + toApp;
            if (drawn.add(key)) {
                sb.append("    ").append(nid(fromApp)).append(" --> ").append(nid(toApp)).append("\n");
            }
        }
    }

    // ── system level ──────────────────────────────────────────────────────────

    private String renderSystemLevel(List<GraphQuery.ApplicationNode> apps, GraphQuery graph) {
        StringBuilder sb = new StringBuilder(FLOWCHART_HEADER);
        for (GraphQuery.ApplicationNode app : apps) {
            sb.append("    ").append(nid(app.id().value())).append("[\"**")
                    .append(app.name()).append("**\\n").append(app.technology())
                    .append(" / ").append(app.packagingType()).append(NODE_CLOSE);
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
                sb.append("    ").append(nid(fromApp)).append(EDGE_LABEL_OPEN)
                        .append(escape(kind)).append("| ").append(nid(dep.toId().value())).append("\n");
            }
        }

        for (GraphQuery.ExternalSystemNode ext : graph.allExternalSystemNodes()) {
            if (!referencedExternals.contains(ext.id().value())) continue;
            String[] shape = externalShape(ext.kind());
            String kindLabel = ext.kind() != null ? ext.kind().toUpperCase() : "";
            sb.append("    ").append(nid(ext.id().value()))
                    .append(shape[0]).append("\"").append(escape(ext.name()))
                    .append("\\n").append(escape(kindLabel)).append("\"").append(shape[1]).append("\n");
        }
        return sb.toString();
    }

    // ── container level ──────────────────────────────────────────────────────

    private String renderContainerLevel(List<GraphQuery.ApplicationNode> apps, GraphQuery graph) {
        StringBuilder sb = new StringBuilder(FLOWCHART_HEADER);

        Map<String, String> compToContainer = buildCompToContainerMap(apps, graph);
        Map<String, Long> epByContainer = graph.entrypointCountPerContainer();

        Set<String> visibleContainers = new LinkedHashSet<>();
        for (GraphQuery.ApplicationNode app : apps) {
            appendContainerSubgraph(sb, app, graph, epByContainer, visibleContainers);
        }

        Set<String> referencedExternals = new LinkedHashSet<>();
        Map<String, Set<String>> edgeKinds = aggregateContainerEdges(
                graph, compToContainer, visibleContainers, referencedExternals);
        appendExternalNodes(sb, graph, referencedExternals);
        appendLabelledEdges(sb, edgeKinds);
        return sb.toString();
    }

    private void appendContainerSubgraph(
            StringBuilder sb,
            GraphQuery.ApplicationNode app,
            GraphQuery graph,
            Map<String, Long> epByContainer,
            Set<String> visibleContainers) {
        sb.append(SUBGRAPH_OPEN).append(nid(app.id().value())).append("[\"")
                .append(escape(app.name())).append(" (").append(app.technology()).append(NODE_CLOSE_PAREN);

        for (GraphQuery.ContainerNode container : graph.containersForApp(AppId.of(app.id().value()))) {
            visibleContainers.add(container.id().value());
            int compCount = graph.componentIdsInContainer(container.id()).size();
            long epCount = epByContainer.getOrDefault(container.id().value(), 0L);
            String label = escape(container.name()) + "\\n" + compCount + " component"
                    + (compCount != 1 ? "s" : "") + (epCount > 0 ? " / " + epCount + " EP" : "");
            sb.append(INDENT8).append(nid(container.id().value())).append("[\"").append(label).append(NODE_CLOSE);
        }
        sb.append(SUBGRAPH_CLOSE);
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

            if (fromC != null && visibleContainers.contains(fromC)
                    && graph.isExternalSystem(dep.toId())) {
                referencedExternals.add(dep.toId().value());
                edgeKinds.computeIfAbsent(fromC + "\0" + dep.toId().value(), k -> new LinkedHashSet<>())
                        .add(nullToEmpty(kind));
                continue;
            }

            String toC = compToContainer.get(dep.toId().value());
            if (fromC == null || toC == null || fromC.equals(toC)) continue;
            if (!visibleContainers.contains(fromC) || !visibleContainers.contains(toC)) continue;
            edgeKinds.computeIfAbsent(fromC + "\0" + toC, k -> new LinkedHashSet<>())
                    .add(nullToEmpty(kind));
        }
        return edgeKinds;
    }

    private void appendExternalNodes(StringBuilder sb, GraphQuery graph, Set<String> referencedExternals) {
        for (GraphQuery.ExternalSystemNode ext : graph.allExternalSystemNodes()) {
            if (!referencedExternals.contains(ext.id().value())) continue;
            String[] shape = externalShape(ext.kind());
            String kindLabel = ext.kind() != null ? ext.kind().toUpperCase() : "";
            sb.append("    ").append(nid(ext.id().value()))
                    .append(shape[0]).append("\"").append(escape(ext.name()))
                    .append("\\n").append(escape(kindLabel)).append("\"").append(shape[1]).append("\n");
        }
    }

    private void appendLabelledEdges(StringBuilder sb, Map<String, Set<String>> edgeKinds) {
        for (Map.Entry<String, Set<String>> entry : edgeKinds.entrySet()) {
            String[] parts = entry.getKey().split("\0", 2);
            String kindLabel = String.join(", ", entry.getValue());
            sb.append("    ").append(nid(parts[0])).append(EDGE_LABEL_OPEN)
                    .append(escape(kindLabel)).append("| ").append(nid(parts[1])).append("\n");
        }
    }

    // ── component level ──────────────────────────────────────────────────────

    private String renderComponentLevel(List<GraphQuery.ApplicationNode> apps, GraphQuery graph) {
        StringBuilder sb = new StringBuilder(FLOWCHART_HEADER);
        for (GraphQuery.ApplicationNode app : apps) {
            appendComponentSubgraph(sb, app, graph);
        }
        appendComponentEdges(sb, apps, graph);
        return sb.toString();
    }

    private void appendComponentSubgraph(
            StringBuilder sb, GraphQuery.ApplicationNode app, GraphQuery graph) {
        sb.append(SUBGRAPH_OPEN).append(nid(app.id().value())).append("[\"")
                .append(escape(app.name())).append(" (").append(app.technology()).append(NODE_CLOSE_PAREN);

        List<GraphQuery.ContainerNode> appContainers = graph.containersForApp(AppId.of(app.id().value()));
        List<GraphQuery.ComponentNode> allComponents = graph.allComponentNodes();
        Map<GraphNodeId, GraphQuery.ComponentNode> compById = new LinkedHashMap<>();
        for (GraphQuery.ComponentNode c : allComponents) compById.put(c.id(), c);

        if (appContainers.isEmpty()) {
            for (GraphNodeId cid : graph.componentIdsOwnedBy(app.id())) {
                renderComponentNode(sb, cid, compById, INDENT8);
            }
        } else {
            for (GraphQuery.ContainerNode container : appContainers) {
                sb.append("        subgraph ").append(nid(container.id().value())).append("[\"")
                        .append(escape(container.name())).append(NODE_CLOSE);
                for (GraphNodeId cid : graph.componentIdsInContainer(container.id())) {
                    renderComponentNode(sb, cid, compById, "            ");
                }
                sb.append("        end\n");
            }
        }
        sb.append(SUBGRAPH_CLOSE);
    }

    private void appendComponentEdges(
            StringBuilder sb, List<GraphQuery.ApplicationNode> apps, GraphQuery graph) {
        Set<String> visibleComps = apps.stream()
                .flatMap(a -> graph.componentIdsOwnedBy(a.id()).stream())
                .map(GraphNodeId::value)
                .collect(Collectors.toSet());

        for (GraphQuery.GraphEdge dep : graph.dependencyEdges()) {
            if (visibleComps.contains(dep.fromId().value()) && visibleComps.contains(dep.toId().value())) {
                String kind = dep.properties().get("kind") instanceof String s ? s : "";
                sb.append("    ").append(nid(dep.fromId().value())).append(EDGE_LABEL_OPEN)
                        .append(escape(kind)).append("| ").append(nid(dep.toId().value())).append("\n");
            }
        }
    }

    private void renderComponentNode(
            StringBuilder sb,
            GraphNodeId cid,
            Map<GraphNodeId, GraphQuery.ComponentNode> byId,
            String indent) {
        GraphQuery.ComponentNode comp = byId.get(cid);
        if (comp == null) return;
        String[] shape = shapeFor(comp);
        String label = (comp.type() != null ? comp.type().name() : "") + "\\n" + escape(comp.name());
        sb.append(indent).append(nid(cid.value()))
                .append(shape[0]).append("\"").append(label).append("\"").append(shape[1]).append("\n");
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
            for (GraphQuery.ContainerNode c : graph.containersForApp(AppId.of(app.id().value()))) {
                for (GraphNodeId cid : graph.componentIdsInContainer(c.id())) {
                    map.put(cid.value(), c.id().value());
                }
            }
        }
        return map;
    }

    private String[] shapeFor(GraphQuery.ComponentNode comp) {
        if (comp.type() == null) return new String[] {"[", "]"};
        return switch (comp.type()) {
            case ENTITY -> new String[] {"[(", ")]"};
            case REST_RESOURCE -> new String[] {"([", "])"};
            default -> new String[] {"[", "]"};
        };
    }

    private String[] externalShape(String kind) {
        if (kind == null) return new String[] {"[", "]"};
        return switch (kind) {
            case "MESSAGE_BROKER", "message_broker" -> new String[] {"[(", ")]"};
            case "REST_API", "rest_api" -> new String[] {"[/", "/]"};
            default -> new String[] {"[", "]"};
        };
    }

    private String nullToEmpty(String s) {
        return StringUtils.isBlank(s) ? "uses" : s;
    }

    private String nid(String id) {
        return id.replaceAll("[^a-zA-Z0-9]", "_");
    }

    private String escape(String s) {
        return Mermaid.escapeLabel(s);
    }
}
