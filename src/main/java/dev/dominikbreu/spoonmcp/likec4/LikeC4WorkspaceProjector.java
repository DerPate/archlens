package dev.dominikbreu.spoonmcp.likec4;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.model.AppEntry;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
import dev.dominikbreu.spoonmcp.model.ids.GraphNodeId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Projects an architecture graph into a {@link LikeC4Document} for workspace-level views. */
public final class LikeC4WorkspaceProjector {

    private static final Set<String> VIEW_RELATIONSHIPS =
            Set.of("DEPENDS_ON", "STATE_HANDOFF", "READS_STATE", "WRITES_STATE", "STARTS_AT", "STARTED_BY");

    private static final Set<String> PRIMARY_COMPONENT_TYPES = Set.of(
            "REST_RESOURCE",
            "SERVICE",
            "EJB_STATELESS",
            "EJB_STATEFUL",
            "EJB_SINGLETON",
            "MESSAGE_DRIVEN_BEAN",
            "SCHEDULER",
            "HTTP_CLIENT",
            "CDI_EVENT_CONSUMER",
            "CDI_EVENT_PRODUCER",
            "REMOTE_SERVICE",
            "REPOSITORY");

    private static final Set<String> MESSAGING_ENTRYPOINT_TYPES =
            Set.of("MESSAGING_CONSUMER", "MESSAGING_PRODUCER", "JMS_CONSUMER");

    /** Creates a projector with default settings. */
    public LikeC4WorkspaceProjector() {}

    /**
     * Projects a workspace-level LikeC4 document for the given application.
     *
     * @param graph the architecture graph
     * @param model the raw architecture model
     * @param app the application entry to scope the view to
     * @param maxNodes the maximum number of component nodes to include
     * @return the projected LikeC4 document
     */
    public LikeC4Document projectWorkspace(
            ArchitectureGraph graph, ArchitectureModel model, AppEntry app, int maxNodes) {
        LikeC4Element system = systemElement(model, app);
        int nodeLimit = Math.max(1, maxNodes);

        List<ArchitectureGraph.GraphNode> scopedComponents = scopedComponents(graph, model, app);
        List<ArchitectureGraph.GraphNode> entrypoints = scopedEntrypoints(graph, model, app).stream()
                .sorted(entrypointPriority())
                .limit(entrypointBudget(nodeLimit))
                .toList();
        Set<GraphNodeId> entrypointComponentIds = componentIds(entrypoints);
        int componentLimit = Math.max(1, nodeLimit - entrypoints.size());

        List<ArchitectureGraph.GraphNode> primaryCandidates = scopedComponents.stream()
                .filter(LikeC4WorkspaceProjector::isPrimaryComponent)
                .sorted(primaryComponentPriority())
                .toList();
        // Also force in direct injection targets of entrypoint-owning components so that
        // services wired to a listener/controller always appear alongside their owner.
        Set<GraphNodeId> candidateIds = ids(primaryCandidates);
        Set<GraphNodeId> injectionTargets =
                graph.findEdgesBetween(union(entrypointComponentIds, candidateIds), Set.of("DEPENDS_ON")).stream()
                        .filter(edge -> entrypointComponentIds.contains(edge.fromId()))
                        .map(ArchitectureGraph.GraphEdge::toId)
                        .filter(candidateIds::contains)
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        Set<GraphNodeId> allForcedIds = union(entrypointComponentIds, injectionTargets);
        List<ArchitectureGraph.GraphNode> primaryComponents =
                selectPrimaryComponents(primaryCandidates, allForcedIds, componentLimit);

        Set<GraphNodeId> primaryIds = ids(primaryComponents);
        int supportingEntityBudget = supportingEntityBudget(nodeLimit, entrypoints.size(), primaryComponents.size());
        List<ArchitectureGraph.GraphNode> supportingEntities = supportingEntityBudget == 0
                ? List.of()
                : scopedComponents.stream()
                        .filter(LikeC4WorkspaceProjector::isEntityComponent)
                        .filter(node -> connectedToAny(graph, node.id(), primaryIds))
                        .sorted(supportingEntityPriority(graph, primaryIds))
                        .limit(supportingEntityBudget)
                        .toList();

        List<ArchitectureGraph.GraphNode> selectedComponents = new ArrayList<>();
        selectedComponents.addAll(primaryComponents);
        selectedComponents.addAll(supportingEntities);

        if (selectedComponents.size() < nodeLimit) {
            Set<GraphNodeId> selectedIds = ids(selectedComponents);
            scopedComponents.stream()
                    .filter(node -> !selectedIds.contains(node.id()))
                    .filter(node -> !isEntityComponent(node))
                    .sorted(fallbackComponentPriority())
                    .limit(nodeLimit - selectedComponents.size())
                    .forEach(selectedComponents::add);
        }

        List<LikeC4Element> components = selectedComponents.stream()
                .map(node -> new LikeC4Element(
                        node.id().value(),
                        "component",
                        title(node.name(), node.id().value()),
                        node.id().value(),
                        metadata(node.properties())))
                .toList();
        List<LikeC4Element> entrypointElements = entrypoints.stream()
                .map(node -> new LikeC4Element(
                        node.id().value(),
                        "entrypoint",
                        entrypointTitle(node),
                        node.id().value(),
                        metadata(node.properties())))
                .toList();

        Set<GraphNodeId> componentIdSet = ids(selectedComponents);
        List<LikeC4Relationship> relationships = graph.findEdgesBetween(componentIdSet, VIEW_RELATIONSHIPS).stream()
                .filter(edge -> !edge.fromId().equals(edge.toId()))
                .map(edge -> new LikeC4Relationship(
                        edge.fromId().value(),
                        edge.toId().value(),
                        relationshipTitle(edge.label(), edge.properties()),
                        edge.label(),
                        Map.of("label", edge.label())))
                .toList();
        Set<GraphNodeId> entrypointIdSet = ids(entrypoints);
        List<LikeC4Relationship> entrypointRelationships =
                graph.findEdgesBetween(union(entrypointIdSet, componentIdSet), Set.of("STARTS_AT")).stream()
                        .filter(edge -> entrypointIdSet.contains(edge.fromId()) && componentIdSet.contains(edge.toId()))
                        .map(edge -> new LikeC4Relationship(
                                edge.fromId().value(),
                                edge.toId().value(),
                                relationshipTitle(edge.label(), edge.properties()),
                                edge.label(),
                                Map.of("label", edge.label())))
                        .toList();

        List<LikeC4DynamicView> dynamicViews = projectMessageBrokerFlows(entrypoints);
        List<LikeC4Element> topicElements = topicElementsFor(dynamicViews);

        List<LikeC4Element> elements = new ArrayList<>();
        elements.add(system);
        elements.addAll(entrypointElements);
        elements.addAll(components);
        elements.addAll(topicElements);

        List<String> baseKinds = new ArrayList<>(List.of("system", "entrypoint", "component"));
        if (!topicElements.isEmpty()) {
            baseKinds.add("queue");
        }

        List<String> systemIds = List.of(system.id());
        List<String> primaryComponentIds =
                primaryComponents.stream().map(node -> node.id().value()).toList();
        List<String> componentIds = components.stream().map(LikeC4Element::id).toList();
        List<String> entrypointIds =
                entrypointElements.stream().map(LikeC4Element::id).toList();
        List<String> containerIds = new ArrayList<>();
        containerIds.add(system.id());
        containerIds.addAll(primaryComponentIds.isEmpty() ? componentIds : primaryComponentIds);
        List<String> componentViewIds = new ArrayList<>();
        componentViewIds.addAll(entrypointIds);
        componentViewIds.addAll(componentIds);

        List<String> warnings = new ArrayList<>();
        if (primaryComponents.isEmpty()) {
            warnings.add("No primary architecture components found for LikeC4 selection");
        }
        if (relationships.isEmpty()) {
            warnings.add("No LikeC4 relationships found between selected components");
        }
        List<String> componentNotes = supportingEntities.isEmpty()
                ? List.of()
                : List.of("Includes supporting entity components connected to selected architecture nodes");

        return new LikeC4Document(
                List.copyOf(baseKinds),
                elements,
                concat(entrypointRelationships, relationships),
                List.of(
                        new LikeC4View("context", "Context", systemIds, List.of()),
                        new LikeC4View("container", "Container", containerIds, List.of()),
                        new LikeC4View("component", "Component", componentViewIds, componentNotes)),
                warnings,
                dynamicViews);
    }

    private static List<LikeC4DynamicView> projectMessageBrokerFlows(List<ArchitectureGraph.GraphNode> entrypoints) {
        List<ArchitectureGraph.GraphNode> messagingNodes = entrypoints.stream()
                .filter(node -> MESSAGING_ENTRYPOINT_TYPES.contains(entrypointTypeOf(node)))
                .toList();
        if (messagingNodes.isEmpty()) {
            return List.of();
        }

        Map<String, List<ArchitectureGraph.GraphNode>> byBroker = messagingNodes.stream()
                .collect(Collectors.groupingBy(node -> brokerOf(node), LinkedHashMap::new, Collectors.toList()));

        List<LikeC4DynamicView> views = new ArrayList<>();
        for (Map.Entry<String, List<ArchitectureGraph.GraphNode>> entry : byBroker.entrySet()) {
            String broker = entry.getKey();
            List<ArchitectureGraph.GraphNode> nodes = entry.getValue();

            List<LikeC4DynamicStep> steps = new ArrayList<>();
            for (ArchitectureGraph.GraphNode node : nodes) {
                String type = entrypointTypeOf(node);
                String channel = channelOf(node);
                String topicId = topicNodeId(broker, channel);
                String ownerComponentId = componentId(node).value();
                if (ownerComponentId.isBlank()) {
                    continue;
                }
                if ("MESSAGING_CONSUMER".equals(type) || "JMS_CONSUMER".equals(type)) {
                    steps.add(new LikeC4DynamicStep(topicId, ownerComponentId, "consumes " + channel));
                } else if ("MESSAGING_PRODUCER".equals(type)) {
                    steps.add(new LikeC4DynamicStep(ownerComponentId, topicId, "publishes to " + channel));
                }
            }

            if (!steps.isEmpty()) {
                String viewId = broker.toLowerCase() + "_flow";
                String viewTitle = broker + " Message Flow";
                views.add(new LikeC4DynamicView(viewId, viewTitle, steps));
            }
        }
        return views;
    }

    private static List<LikeC4Element> topicElementsFor(List<LikeC4DynamicView> dynamicViews) {
        Set<String> seen = new LinkedHashSet<>();
        List<LikeC4Element> elements = new ArrayList<>();
        for (LikeC4DynamicView view : dynamicViews) {
            for (LikeC4DynamicStep step : view.steps()) {
                collectTopicId(step.sourceId(), seen, elements);
                collectTopicId(step.targetId(), seen, elements);
            }
        }
        return elements;
    }

    private static void collectTopicId(String id, Set<String> seen, List<LikeC4Element> elements) {
        if (id.startsWith("topic:") && seen.add(id)) {
            String[] parts = id.split(":", 3);
            String broker = parts.length > 1 ? parts[1] : "";
            String channel = parts.length > 2 ? parts[2] : id;
            String title = channel.isBlank() ? id : channel;
            elements.add(new LikeC4Element(id, "queue", title, id, Map.of("broker", broker)));
        }
    }

    private static String topicNodeId(String broker, String channel) {
        return "topic:" + broker + ":" + channel;
    }

    private static String entrypointTypeOf(ArchitectureGraph.GraphNode node) {
        return String.valueOf(node.properties().getOrDefault("entrypointType", ""));
    }

    private static String brokerOf(ArchitectureGraph.GraphNode node) {
        String broker =
                String.valueOf(node.properties().getOrDefault("broker", "")).trim();
        return broker.isBlank() ? "UNKNOWN" : broker;
    }

    private static String channelOf(ArchitectureGraph.GraphNode node) {
        String channel = String.valueOf(node.properties().getOrDefault("channelName", ""))
                .trim();
        if (!channel.isBlank()) {
            return channel;
        }
        return String.valueOf(node.properties().getOrDefault("topic", "")).trim();
    }

    private static List<ArchitectureGraph.GraphNode> scopedComponents(
            ArchitectureGraph graph, ArchitectureModel model, AppEntry app) {
        if (app != null && !app.componentIds.isEmpty()) {
            return graph.nodesByComponentIds(app.componentIds);
        }
        if (model != null && model.components != null && !model.components.isEmpty()) {
            return graph.nodesByComponentIds(
                    model.components.stream().map(c -> c.id).toList());
        }
        if (app != null) {
            return graph.componentNodesOwnedBy(app.id);
        }
        return List.of();
    }

    private static List<ArchitectureGraph.GraphNode> scopedEntrypoints(
            ArchitectureGraph graph, ArchitectureModel model, AppEntry app) {
        if (model == null || model.entrypoints == null || model.entrypoints.isEmpty()) {
            return List.of();
        }
        Set<ComponentId> scope =
                app != null && !app.componentIds.isEmpty() ? new HashSet<>(app.componentIds) : Set.of();
        List<EntrypointId> ids = model.entrypoints.stream()
                .filter(ep -> scope.isEmpty() || scope.contains(ep.componentId))
                .map(ep -> ep.id)
                .toList();
        return graph.nodesByEntrypointIds(ids);
    }

    private static Set<GraphNodeId> ids(List<ArchitectureGraph.GraphNode> nodes) {
        Set<GraphNodeId> ids = new LinkedHashSet<>();
        nodes.forEach(node -> ids.add(node.id()));
        return ids;
    }

    private static Set<GraphNodeId> componentIds(List<ArchitectureGraph.GraphNode> nodes) {
        Set<GraphNodeId> ids = new LinkedHashSet<>();
        nodes.stream()
                .map(LikeC4WorkspaceProjector::componentId)
                .filter(id -> !id.value().isBlank())
                .forEach(ids::add);
        return ids;
    }

    private static Set<GraphNodeId> union(Set<GraphNodeId> left, Set<GraphNodeId> right) {
        Set<GraphNodeId> ids = new LinkedHashSet<>(left);
        ids.addAll(right);
        return ids;
    }

    private static int entrypointBudget(int maxNodes) {
        return Math.max(1, Math.min(8, maxNodes / 3));
    }

    private static int supportingEntityBudget(int maxNodes, int entrypointCount, int primaryCount) {
        int used = entrypointCount + primaryCount;
        if (used >= maxNodes) {
            return 0;
        }
        return Math.min(maxNodes - used, Math.min(4, Math.max(1, maxNodes / 5)));
    }

    private static List<ArchitectureGraph.GraphNode> selectPrimaryComponents(
            List<ArchitectureGraph.GraphNode> candidates, Set<GraphNodeId> forcedIds, int limit) {
        List<ArchitectureGraph.GraphNode> selected = new ArrayList<>();
        Set<GraphNodeId> selectedIds = new HashSet<>();
        candidates.stream()
                .filter(node -> forcedIds.contains(node.id()))
                .forEach(node -> addSelected(selected, selectedIds, node, limit));
        candidates.stream()
                .filter(node -> !forcedIds.contains(node.id()))
                .forEach(node -> addSelected(selected, selectedIds, node, limit));
        return selected;
    }

    private static void addSelected(
            List<ArchitectureGraph.GraphNode> selected,
            Set<GraphNodeId> selectedIds,
            ArchitectureGraph.GraphNode node,
            int limit) {
        if (selected.size() < limit && selectedIds.add(node.id())) {
            selected.add(node);
        }
    }

    private static boolean connectedToAny(ArchitectureGraph graph, GraphNodeId nodeId, Set<GraphNodeId> selectedIds) {
        if (selectedIds.isEmpty()) {
            return false;
        }
        Set<GraphNodeId> ids = new HashSet<>(selectedIds);
        ids.add(nodeId);
        return graph.findEdgesBetween(ids, VIEW_RELATIONSHIPS).stream()
                .anyMatch(edge -> nodeId.equals(edge.fromId()) || nodeId.equals(edge.toId()));
    }

    private static Comparator<ArchitectureGraph.GraphNode> primaryComponentPriority() {
        return Comparator.comparingInt(LikeC4WorkspaceProjector::primaryRank)
                .thenComparing(LikeC4WorkspaceProjector::workflowRelevant, Comparator.reverseOrder())
                .thenComparing(LikeC4WorkspaceProjector::businessRelevant, Comparator.reverseOrder())
                .thenComparingInt(node -> -workflowBridgeScore(node))
                .thenComparingInt(LikeC4WorkspaceProjector::noiseScore)
                .thenComparingInt(node -> -architecturalWeight(node))
                .thenComparing(ArchitectureGraph.GraphNode::name);
    }

    private static Comparator<ArchitectureGraph.GraphNode> entrypointPriority() {
        return Comparator.comparingInt(LikeC4WorkspaceProjector::entrypointRank)
                .thenComparing(ArchitectureGraph.GraphNode::name)
                .thenComparing(node -> node.id().value());
    }

    private static Comparator<ArchitectureGraph.GraphNode> fallbackComponentPriority() {
        return Comparator.comparingInt(LikeC4WorkspaceProjector::noiseScore)
                .thenComparingInt(node -> -architecturalWeight(node))
                .thenComparing(ArchitectureGraph.GraphNode::name);
    }

    private static Comparator<ArchitectureGraph.GraphNode> supportingEntityPriority(
            ArchitectureGraph graph, Set<GraphNodeId> primaryIds) {
        return Comparator.comparingInt(
                        (ArchitectureGraph.GraphNode node) -> primaryConnectionCount(graph, node.id(), primaryIds))
                .reversed()
                .thenComparing(LikeC4WorkspaceProjector::businessRelevant, Comparator.reverseOrder())
                .thenComparingInt(LikeC4WorkspaceProjector::noiseScore)
                .thenComparing(ArchitectureGraph.GraphNode::name);
    }

    private static int primaryConnectionCount(
            ArchitectureGraph graph, GraphNodeId nodeId, Set<GraphNodeId> primaryIds) {
        Set<GraphNodeId> ids = new HashSet<>(primaryIds);
        ids.add(nodeId);
        return (int) graph.findEdgesBetween(ids, VIEW_RELATIONSHIPS).stream()
                .filter(edge -> nodeId.equals(edge.fromId()) || nodeId.equals(edge.toId()))
                .count();
    }

    private static int primaryRank(ArchitectureGraph.GraphNode node) {
        return switch (componentType(node)) {
            case "REST_RESOURCE", "MESSAGE_DRIVEN_BEAN", "SCHEDULER", "CDI_EVENT_CONSUMER" -> 0;
            case "SERVICE", "EJB_STATELESS", "EJB_STATEFUL", "EJB_SINGLETON" -> 1;
            case "REPOSITORY" -> 2;
            case "REMOTE_SERVICE", "HTTP_CLIENT", "CDI_EVENT_PRODUCER" -> 3;
            default -> 3;
        };
    }

    private static boolean isPrimaryComponent(ArchitectureGraph.GraphNode node) {
        return PRIMARY_COMPONENT_TYPES.contains(componentType(node));
    }

    private static boolean isEntityComponent(ArchitectureGraph.GraphNode node) {
        return "ENTITY".equals(componentType(node));
    }

    private static String componentType(ArchitectureGraph.GraphNode node) {
        return String.valueOf(node.properties().getOrDefault("componentType", ""));
    }

    private static GraphNodeId componentId(ArchitectureGraph.GraphNode node) {
        return GraphNodeId.of(String.valueOf(node.properties().getOrDefault("componentId", "")));
    }

    private static int entrypointRank(ArchitectureGraph.GraphNode node) {
        return switch (String.valueOf(node.properties().getOrDefault("entrypointType", ""))) {
            case "MESSAGING_CONSUMER" -> 0;
            case "SCHEDULER" -> 1;
            case "REST_ENDPOINT" -> 2;
            case "MAIN_METHOD" -> 3;
            default -> 4;
        };
    }

    private static String entrypointTitle(ArchitectureGraph.GraphNode node) {
        Map<String, Object> properties = node.properties();
        String type = String.valueOf(properties.getOrDefault("entrypointType", ""));
        if ("REST_ENDPOINT".equals(type)) {
            return (String.valueOf(properties.getOrDefault("httpMethod", "")).trim() + " "
                            + String.valueOf(properties.getOrDefault("path", ""))
                                    .trim())
                    .trim();
        }
        if ("MESSAGING_CONSUMER".equals(type)) {
            return (String.valueOf(properties.getOrDefault("broker", "")).trim() + " "
                            + String.valueOf(properties.getOrDefault("channelName", ""))
                                    .trim())
                    .trim();
        }
        return title(node.name(), node.id().value());
    }

    private static String relationshipTitle(String label, Map<String, Object> properties) {
        Object kind = properties.get("kind");
        if (kind != null && !kind.toString().isBlank()) {
            return kind.toString();
        }
        return label.toLowerCase().replace('_', ' ');
    }

    private static boolean workflowRelevant(ArchitectureGraph.GraphNode node) {
        return Boolean.TRUE.equals(node.properties().get("workflowRelevant"));
    }

    private static boolean businessRelevant(ArchitectureGraph.GraphNode node) {
        return Boolean.TRUE.equals(node.properties().get("businessRelevant"));
    }

    private static int workflowBridgeScore(ArchitectureGraph.GraphNode node) {
        return intProp(node, "workflowBridgeScore");
    }

    private static int architecturalWeight(ArchitectureGraph.GraphNode node) {
        return intProp(node, "architecturalWeight");
    }

    private static int noiseScore(ArchitectureGraph.GraphNode node) {
        return intProp(node, "noiseScore");
    }

    private static int intProp(ArchitectureGraph.GraphNode node, String key) {
        Object value = node.properties().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
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
        String workspace = model != null ? model.workspacePath : null;
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
        return properties == null ? Map.of() : properties;
    }

    private static List<LikeC4Relationship> concat(List<LikeC4Relationship> first, List<LikeC4Relationship> second) {
        List<LikeC4Relationship> combined = new ArrayList<>(first);
        combined.addAll(second);
        return combined;
    }

    private static void putIfPresent(Map<String, Object> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }

    private static String title(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
