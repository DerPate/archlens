package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataFlowTracerTest {

    private final DataFlowTracer tracer = new DataFlowTracer();

    // ── happy paths ──────────────────────────────────────────────────────────────

    @Test
    void tracesThroughServiceToRepositorySink() {
        ArchitectureModel model = buildModel();

        addCallEdge(model, "comp:OrderResource", "create", "comp:OrderService", "create",
                    Map.of("order", "order"));
        addCallEdge(model, "comp:OrderService", "create", "comp:OrderRepository", "save",
                    Map.of("order", "entity"));

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> {
            assertThat(p.trackedParam).isEqualTo("order");
            assertThat(p.sinks).anySatisfy(s -> {
                assertThat(s.kind).isEqualTo("persistence");
                assertThat(s.componentName).isEqualTo("OrderRepository");
                assertThat(s.method).isEqualTo("save");
            });
        });
    }

    @Test
    void tracksParameterRenameAcrossHops() {
        ArchitectureModel model = buildModel();

        addCallEdge(model, "comp:OrderResource", "create", "comp:OrderService", "create",
                    Map.of("order", "dto"));
        addCallEdge(model, "comp:OrderService", "create", "comp:OrderRepository", "save",
                    Map.of("dto", "entity"));

        List<DataFlowPath> paths = tracer.trace(model);

        DataFlowPath path = paths.stream()
            .filter(p -> p.trackedParam.equals("order"))
            .findFirst().orElseThrow();

        List<String> localNames = path.steps.stream().map(s -> s.localName).toList();
        assertThat(localNames).containsSequence("order", "dto");
    }

    @Test
    void classifiesMessagingCallKindAsSink() {
        ArchitectureModel model = buildModel();

        addCallEdgeWithKind(model, "comp:OrderResource", "create", "comp:OrderService", "create",
                            Map.of("order", "order"), "direct");
        addCallEdgeWithKind(model, "comp:OrderService", "create", "comp:OrderService", "emit",
                            Map.of(), "messaging");

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p -> p.sinks.stream()
            .anyMatch(s -> "messaging".equals(s.kind)));
    }

    @Test
    void classifiesEventBusCallKindAsSink() {
        ArchitectureModel model = buildModel();

        addCallEdgeWithKind(model, "comp:OrderResource", "create", "comp:OrderService", "publish",
                            Map.of("order", "event"), "event-bus");

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).anySatisfy(p ->
            p.sinks.stream().anyMatch(s -> "event-bus".equals(s.kind)));
    }

    @Test
    void omitsPathsWithNoSinks() {
        ArchitectureModel model = buildModel();
        // only edges between service-tier components, no sink types
        addCallEdge(model, "comp:OrderResource", "create", "comp:OrderService", "create",
                    Map.of("order", "order"));

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).noneMatch(p -> p.trackedParam.equals("order") && p.sinks.isEmpty() == false);
        // path should simply be absent (no sinks found)
        assertThat(paths.stream().filter(p -> p.trackedParam.equals("order")).toList()).isEmpty();
    }

    @Test
    void doesNotLoopOnCyclicCallGraph() {
        ArchitectureModel model = buildModel();

        addCallEdge(model, "comp:OrderResource", "create", "comp:OrderService", "create",
                    Map.of("order", "order"));
        addCallEdge(model, "comp:OrderService", "create", "comp:OrderResource", "create",
                    Map.of("order", "order")); // cycle back

        // must terminate without exception
        List<DataFlowPath> paths = tracer.trace(model);
        assertThat(paths).isNotNull();
    }

    @Test
    void producesNoPathsWhenNoCallEdges() {
        ArchitectureModel model = buildModel();
        assertThat(tracer.trace(model)).isEmpty();
    }

    // ── integration: real quarkus-sample ────────────────────────────────────────

    @Test
    void integrationQuarkusSampleHasPersistenceSink() {
        ArchitectureModel model = ExtractorTestBase.buildQuarkusModel();
        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths)
            .as("at least one persistence sink should be found in quarkus-sample")
            .anySatisfy(p -> assertThat(p.sinks)
                .anySatisfy(s -> assertThat(s.kind).isEqualTo("persistence")));
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static ArchitectureModel buildModel() {
        ArchitectureModel model = new ArchitectureModel("test");

        model.components.add(comp("OrderResource",   ComponentType.REST_RESOURCE));
        model.components.add(comp("OrderService",    ComponentType.SERVICE));
        model.components.add(comp("OrderRepository", ComponentType.REPOSITORY));

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:create";
        ep.name = "create";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.httpMethod = "POST";
        ep.componentId = "comp:OrderResource";
        ep.parameters.add("order");
        model.entrypoints.add(ep);

        return model;
    }

    private static Component comp(String name, ComponentType type) {
        Component c = new Component();
        c.id = "comp:" + name;
        c.name = name;
        c.type = type;
        return c;
    }

    private static void addCallEdge(ArchitectureModel model,
                                     String fromComp, String fromMethod,
                                     String toComp, String toMethod,
                                     Map<String, String> paramMapping) {
        addCallEdgeWithKind(model, fromComp, fromMethod, toComp, toMethod, paramMapping, "direct");
    }

    private static void addCallEdgeWithKind(ArchitectureModel model,
                                             String fromComp, String fromMethod,
                                             String toComp, String toMethod,
                                             Map<String, String> paramMapping,
                                             String callKind) {
        CallEdge e = new CallEdge();
        e.id = "call:" + fromComp + "#" + fromMethod + "->" + toComp + "#" + toMethod;
        e.fromComponentId = fromComp;
        e.fromMethod = fromMethod;
        e.toComponentId = toComp;
        e.toMethod = toMethod;
        e.callKind = callKind;
        e.paramMapping.putAll(paramMapping);
        model.callEdges.add(e);
    }
}
