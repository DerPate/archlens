package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.CallEdge;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.DataFlowPath;
import dev.dominikbreu.spoonmcp.model.DataFlowSink;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataFlowTracerMultiParamTest {

    private final DataFlowTracer tracer = new DataFlowTracer();

    @Test
    void producesOnePathPerTrackedParam_withSharedCallGraph() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("Handler", ComponentType.SERVICE));
        model.components.add(comp("Repo", ComponentType.REPOSITORY));

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:handle";
        ep.name = "handle";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.componentId = "comp:Handler";
        ep.parameters.addAll(List.of("userId", "orderId", "sessionId"));
        model.entrypoints.add(ep);

        CallEdge edge = new CallEdge();
        edge.id = "call:Handler#handle->Repo#save";
        edge.fromComponentId = "comp:Handler";
        edge.fromMethod = "handle";
        edge.toComponentId = "comp:Repo";
        edge.toMethod = "save";
        edge.callKind = "direct";
        edge.paramMapping.put("userId", "id");
        edge.paramMapping.put("orderId", "entityId");
        model.callEdges.add(edge);

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).hasSizeGreaterThanOrEqualTo(2);
        assertThat(paths).anySatisfy(p -> {
            assertThat(p.trackedParam).isEqualTo("userId");
            assertThat(p.sinks).anyMatch(s -> s.kind == DataFlowSink.Kind.PERSISTENCE);
        });
        assertThat(paths).anySatisfy(p -> {
            assertThat(p.trackedParam).isEqualTo("orderId");
            assertThat(p.sinks).anyMatch(s -> s.kind == DataFlowSink.Kind.PERSISTENCE);
        });
        List<DataFlowSink> userSinks = paths.stream()
                .filter(p -> "userId".equals(p.trackedParam))
                .flatMap(p -> p.sinks.stream())
                .toList();
        List<DataFlowSink> orderSinks = paths.stream()
                .filter(p -> "orderId".equals(p.trackedParam))
                .flatMap(p -> p.sinks.stream())
                .toList();
        assertThat(userSinks).doesNotContainAnyElementsOf(orderSinks);
    }

    @Test
    void doesNotLoopOnCyclicCallGraph() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(comp("A", ComponentType.SERVICE));
        model.components.add(comp("B", ComponentType.SERVICE));
        model.components.add(comp("Repo", ComponentType.REPOSITORY));

        Entrypoint ep = new Entrypoint();
        ep.id = "ep:start";
        ep.name = "start";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.componentId = "comp:A";
        ep.parameters.add("data");
        model.entrypoints.add(ep);

        addEdge(model, "comp:A", "start", "comp:B", "process", Map.of("data", "d"), "direct");
        addEdge(model, "comp:B", "process", "comp:A", "start", Map.of("d", "data"), "direct");
        addEdge(model, "comp:B", "process", "comp:Repo", "save", Map.of("d", "entity"), "direct");

        List<DataFlowPath> paths = tracer.trace(model);

        assertThat(paths).isNotNull();
    }

    private static Component comp(String name, ComponentType type) {
        Component c = new Component();
        c.id = "comp:" + name;
        c.name = name;
        c.type = type;
        return c;
    }

    private static void addEdge(
            ArchitectureModel model,
            String fromComp,
            String fromMethod,
            String toComp,
            String toMethod,
            Map<String, String> mapping,
            String kind) {
        CallEdge e = new CallEdge();
        e.id = "call:" + fromComp + "#" + fromMethod + "->" + toComp + "#" + toMethod;
        e.fromComponentId = fromComp;
        e.fromMethod = fromMethod;
        e.toComponentId = toComp;
        e.toMethod = toMethod;
        e.callKind = kind;
        e.paramMapping.putAll(mapping);
        model.callEdges.add(e);
    }
}
