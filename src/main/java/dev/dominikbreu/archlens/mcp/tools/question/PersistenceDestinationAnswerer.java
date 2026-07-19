package dev.dominikbreu.archlens.mcp.tools.question;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.cache.GraphQuery.ComponentNode;
import dev.dominikbreu.archlens.cache.GraphQuery.DataFlowPathNode;
import dev.dominikbreu.archlens.cache.GraphQuery.DataFlowSinkNode;
import dev.dominikbreu.archlens.cache.GraphQuery.DataSourceNode;
import dev.dominikbreu.archlens.cache.GraphQuery.EntrypointNode;
import dev.dominikbreu.archlens.cache.GraphQuery.GraphNode;
import dev.dominikbreu.archlens.cache.GraphQuery.PersistenceOperationNode;
import dev.dominikbreu.archlens.cache.GraphQuery.PersistenceUnitNode;
import dev.dominikbreu.archlens.mcp.tools.ToolArgs;
import dev.dominikbreu.archlens.model.ids.EntrypointId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Answers the {@code persistence_destination} intent: origin, transformations, operation, entity, and datasource topology. */
public final class PersistenceDestinationAnswerer {

    private PersistenceDestinationAnswerer() {}

    /**
     * Answers where a tracked value is ultimately persisted.
     *
     * @param graph the graph to query
     * @param args {@code entrypoint}/{@code component}/{@code query} and optional {@code param} selectors
     * @param recorder the graph-operation recorder (currently unused by this answerer)
     * @return the persistence-destination answer
     */
    public static Answer answer(GraphQuery graph, Map<String, Object> args, QueryPlanRecorder recorder) {
        Answer result = new Answer();
        String entrypointRef = QuestionSupport.first(args, "entrypoint", "entrypointId", "entrypointName");
        String componentRef = QuestionSupport.first(args, "component", "componentId");
        String query = ToolArgs.getString(args, "query");
        String param = ToolArgs.getString(args, "param");

        EntrypointNode entrypoint =
                entrypointRef != null ? QuestionSupport.entrypoint(graph, entrypointRef, result) : null;
        ComponentNode component = componentRef != null ? QuestionSupport.component(graph, componentRef, result) : null;
        if (entrypointRef == null && componentRef == null && query == null) {
            result.unresolved.add("missing-subject: provide entrypoint, component, or query");
        }
        if (entrypoint != null) result.subject(QuestionSupport.nodeMap(entrypoint));
        else if (component != null) result.subject(QuestionSupport.nodeMap(component));
        else if (query != null) result.subject(Map.of("query", query));

        List<Map<String, Object>> origins = new ArrayList<>();
        List<Map<String, Object>> transformations = new ArrayList<>();
        List<Map<String, Object>> operations = new ArrayList<>();
        List<Map<String, Object>> destinations = new ArrayList<>();

        if (entrypoint != null) {
            origins.add(QuestionSupport.nodeMap(entrypoint));
            for (DataFlowPathNode path : graph.allDataFlowPaths()) {
                if (!Objects.equals(
                        path.entrypointId(),
                        EntrypointId.deserialize(entrypoint.id().serialize()))) continue;
                if (param != null && !param.equals(path.trackedParam())) continue;
                graph.pathDataFlowSteps(path.id()).forEach(step -> transformations.add(QuestionSupport.nodeMap(step)));
                for (DataFlowSinkNode sink : graph.pathSinks(path.id())) {
                    if (sink.sinkKind() == null
                            || !"persistence".equals(sink.sinkKind().value())) continue;
                    Map<String, Object> operation = QuestionSupport.nodeMap(sink);
                    operations.add(operation);
                    destinations.add(destinationFor(graph, sink.entityType(), sink, result));
                }
            }
        }

        for (GraphNode node : graph.findNodes("PersistenceOperation", query, Map.of(), QuestionSupport.DEFAULT_LIMIT)) {
            if (!(node instanceof PersistenceOperationNode operation)) continue;
            if (component != null
                    && !Objects.equals(
                            component.id().serialize(),
                            operation.componentId() != null
                                    ? operation.componentId().serialize()
                                    : null)) continue;
            operations.add(QuestionSupport.nodeMap(operation));
            destinations.add(destinationFor(graph, operation.entityType(), operation, result));
        }

        if (operations.isEmpty()) result.unresolved.add("no-persistence-operation-matched");
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("origins", QuestionSupport.distinct(origins));
        answer.put("transformations", QuestionSupport.distinct(transformations));
        answer.put("operations", QuestionSupport.distinct(operations));
        answer.put("destinations", QuestionSupport.distinct(destinations));
        result.answer(answer);
        return result;
    }

    private static Map<String, Object> destinationFor(
            GraphQuery graph, String entityType, GraphNode operation, Answer result) {
        Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("entityType", entityType);
        List<PersistenceUnitNode> units =
                graph.findNodes("PersistenceUnit", null, Map.of(), QuestionSupport.DEFAULT_LIMIT).stream()
                        .filter(PersistenceUnitNode.class::isInstance)
                        .map(PersistenceUnitNode.class::cast)
                        .filter(unit ->
                                entityType != null && unit.managedClasses().contains(entityType))
                        .toList();
        if (units.isEmpty()) {
            List<PersistenceUnitNode> all =
                    graph.findNodes("PersistenceUnit", null, Map.of(), QuestionSupport.DEFAULT_LIMIT).stream()
                            .filter(PersistenceUnitNode.class::isInstance)
                            .map(PersistenceUnitNode.class::cast)
                            .toList();
            if (all.size() == 1) {
                units = all;
                result.ambiguous.add(
                        "single-persistence-unit-fallback:" + all.getFirst().name());
            }
        }
        if (units.size() != 1) {
            result.unresolved.add("persistence-unit-not-uniquely-resolved:" + Objects.toString(entityType, "unknown"));
            destination.put(
                    "persistenceUnits",
                    units.stream().map(QuestionSupport::nodeMap).toList());
            return destination;
        }
        PersistenceUnitNode unit = units.getFirst();
        destination.put("persistenceUnit", QuestionSupport.nodeMap(unit));
        List<DataSourceNode> dataSources = QuestionSupport.outgoingNodes(graph, unit.id(), "USES_DATASOURCE").stream()
                .filter(DataSourceNode.class::isInstance)
                .map(DataSourceNode.class::cast)
                .toList();
        destination.put(
                "dataSources",
                dataSources.stream().map(QuestionSupport::nodeMap).toList());
        if (dataSources.isEmpty()) result.unresolved.add("datasource-not-resolved:" + unit.name());
        List<Map<String, Object>> externalSystems = new ArrayList<>();
        for (DataSourceNode dataSource : dataSources) {
            if (dataSource.unresolved()) result.unresolved.add("datasource-unresolved:" + dataSource.name());
            QuestionSupport.outgoingNodes(graph, dataSource.id(), "CONNECTS_TO").stream()
                    .map(QuestionSupport::nodeMap)
                    .forEach(externalSystems::add);
        }
        destination.put("externalSystems", QuestionSupport.distinct(externalSystems));
        if (!dataSources.isEmpty() && externalSystems.isEmpty()) {
            result.unresolved.add("external-database-endpoint-not-resolved:" + unit.name());
        }
        List<Map<String, Object>> chain = new ArrayList<>();
        GraphNode operationNode = graph.node(operation.id());
        GraphNode unitNode = graph.node(unit.id());
        if (operationNode != null) chain.add(QuestionSupport.nodeMap(operationNode));
        if (unitNode != null) chain.add(QuestionSupport.nodeMap(unitNode));
        dataSources.stream().map(QuestionSupport::nodeMap).forEach(chain::add);
        for (DataSourceNode dataSource : dataSources) {
            QuestionSupport.outgoingNodes(graph, dataSource.id(), "CONNECTS_TO").stream()
                    .map(QuestionSupport::nodeMap)
                    .forEach(chain::add);
        }
        destination.put("evidenceChain", chain);
        return destination;
    }
}
