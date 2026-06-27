package dev.dominikbreu.archlens.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.model.*;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.EntrypointId;
import org.junit.jupiter.api.Test;

class MermaidCallFlowRendererTest {

    private final MermaidCallFlowRenderer renderer = new MermaidCallFlowRenderer();

    @Test
    void outputStartsWithFlowchartDirective() {
        var r = buildGraph(model(3), flow(3));
        assertThat(renderer.render(r.flowNode(), r.graph())).startsWith("flowchart TD");
    }

    @Test
    void containsClientNode() {
        var r = buildGraph(model(3), flow(3));
        assertThat(renderer.render(r.flowNode(), r.graph())).contains("Client([Client])");
    }

    @Test
    void containsEachComponentBySimpleName() {
        var r = buildGraph(model(3), flow(3));
        String out = renderer.render(r.flowNode(), r.graph());
        for (int i = 0; i < 3; i++) {
            assertThat(out).contains("Comp" + i);
        }
    }

    @Test
    void repositoryRendersAsCylinder() {
        ArchitectureModel m = model(2);
        m.components.get(1).type = ComponentType.REPOSITORY;
        var r = buildGraph(m, flow(2));
        assertThat(renderer.render(r.flowNode(), r.graph())).contains("[(Comp1)]");
    }

    @Test
    void httpClientRendersAsParallelogram() {
        ArchitectureModel m = model(2);
        m.components.get(1).type = ComponentType.HTTP_CLIENT;
        var r = buildGraph(m, flow(2));
        assertThat(renderer.render(r.flowNode(), r.graph())).contains("[/Comp1/]");
    }

    @Test
    void schedulerRendersAsStadium() {
        ArchitectureModel m = model(2);
        m.components.get(1).type = ComponentType.SCHEDULER;
        var r = buildGraph(m, flow(2));
        assertThat(renderer.render(r.flowNode(), r.graph())).contains("([Comp1])");
    }

    @Test
    void containsForwardEdgesWithViaLabel() {
        RuntimeFlow f = flow(3);
        f.edges.get(0).label = "processOrder";
        f.edges.get(1).label = "save";
        var r = buildGraph(model(3), f);
        String out = renderer.render(r.flowNode(), r.graph());
        assertThat(out).contains("-->|processOrder|");
        assertThat(out).contains("-->|save|");
    }

    @Test
    void branchingFlowRendersBothBranches() {
        ArchitectureModel m = model(3);
        RuntimeFlow f = new RuntimeFlow();
        f.id = "flow:test";
        f.entrypointId = EntrypointId.deserialize("test");
        for (int i = 0; i < 3; i++) {
            RuntimeFlowStep s = new RuntimeFlowStep();
            s.order = i;
            s.componentId = ComponentId.of("Comp" + i);
            s.componentName = "Comp" + i;
            s.componentType = "SERVICE";
            s.via = "call";
            f.steps.add(s);
        }
        f.edges.add(new RuntimeFlow.FlowEdge(ComponentId.of("Comp0"), ComponentId.of("Comp1"), "doB"));
        f.edges.add(new RuntimeFlow.FlowEdge(ComponentId.of("Comp0"), ComponentId.of("Comp2"), "doC"));

        var r = buildGraph(m, f);
        String out = renderer.render(r.flowNode(), r.graph());
        assertThat(out).contains("Comp0 -->|doB| Comp1");
        assertThat(out).contains("Comp0 -->|doC| Comp2");
        assertThat(out).doesNotContain("Comp1 -->");
    }

    @Test
    void clientEdgeShowsHttpMethodAndPath() {
        ArchitectureModel m = model(2);
        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize("test");
        ep.httpMethod = "POST";
        ep.path = "/orders";
        ep.componentId = ComponentId.of("Comp0");
        m.entrypoints.add(ep);
        RuntimeFlow f = flow(2);
        f.entrypointId = ep.id;
        var r = buildGraph(m, f);
        assertThat(renderer.render(r.flowNode(), r.graph())).contains("POST /orders");
    }

    @Test
    void clientEdgeShowsChannelNameForMessaging() {
        ArchitectureModel m = model(2);
        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize("test");
        ep.channelName = "order-events";
        ep.componentId = ComponentId.of("Comp0");
        m.entrypoints.add(ep);
        RuntimeFlow f = flow(2);
        f.entrypointId = ep.id;
        var r = buildGraph(m, f);
        assertThat(renderer.render(r.flowNode(), r.graph())).contains("order-events");
    }

    @Test
    void selfEdgesAreNotRendered() {
        ArchitectureModel m = model(2);
        RuntimeFlow f = new RuntimeFlow();
        f.id = "flow:test";
        f.entrypointId = EntrypointId.deserialize("test");
        RuntimeFlowStep s0 = new RuntimeFlowStep();
        s0.order = 0;
        s0.componentId = ComponentId.of("Comp0");
        s0.componentName = "Comp0";
        f.steps.add(s0);
        RuntimeFlowStep s1 = new RuntimeFlowStep();
        s1.order = 1;
        s1.componentId = ComponentId.of("Comp1");
        s1.componentName = "Comp1";
        f.steps.add(s1);
        f.edges.add(new RuntimeFlow.FlowEdge(ComponentId.of("Comp0"), ComponentId.of("Comp0"), "helper"));
        f.edges.add(new RuntimeFlow.FlowEdge(ComponentId.of("Comp0"), ComponentId.of("Comp1"), "process"));

        var r = buildGraph(m, f);
        String out = renderer.render(r.flowNode(), r.graph());
        assertThat(out).doesNotContain("Comp0 -->|helper| Comp0");
        assertThat(out).contains("-->|process| Comp1");
    }

    @Test
    void noReturnArrowsRendered() {
        var r = buildGraph(model(4), flow(4));
        String out = renderer.render(r.flowNode(), r.graph());
        assertThat(out).doesNotContain("-->>");
        assertThat(out).doesNotContain("result");
    }

    @Test
    void emptyFlowProducesFallbackNote() {
        String out = renderer.render(null, null);
        assertThat(out).contains("flowchart TD");
        assertThat(out).contains("no flow steps");
    }

    @Test
    void duplicateComponentNamesGetSuffix() {
        ArchitectureModel m = new ArchitectureModel("test");
        for (int i = 0; i < 2; i++) {
            Component c = new Component();
            c.id = ComponentId.of("pkg" + i + ".Service");
            c.name = "Service";
            c.type = ComponentType.SERVICE;
            m.components.add(c);
        }
        RuntimeFlow f = new RuntimeFlow();
        f.id = "flow:test";
        f.entrypointId = EntrypointId.deserialize("test");
        for (int i = 0; i < 2; i++) {
            RuntimeFlowStep s = new RuntimeFlowStep();
            s.order = i;
            s.componentId = ComponentId.of("pkg" + i + ".Service");
            s.componentName = "Service";
            s.componentType = "SERVICE";
            s.via = "call";
            f.steps.add(s);
        }
        var r = buildGraph(m, f);
        String out = renderer.render(r.flowNode(), r.graph());
        assertThat(out).contains("Service_1");
        assertThat(out).contains("Service_2");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    record GraphResult(GraphQuery.RuntimeFlowNode flowNode, GraphQuery graph) {}

    private GraphResult buildGraph(ArchitectureModel m, RuntimeFlow f) {
        // ensure a matching entrypoint exists so resolveEntrypoint can find the flow
        if (f.entrypointId != null && m.entrypoints.stream().noneMatch(e -> e.id.equals(f.entrypointId))) {
            Entrypoint ep = new Entrypoint();
            ep.id = f.entrypointId;
            ep.componentId = f.steps.isEmpty() ? ComponentId.of("unknown") : f.steps.getFirst().componentId;
            m.entrypoints.add(ep);
        }
        m.runtimeFlows.add(f);
        GraphQuery graph = GraphQuery.from(m);
        GraphQuery.RuntimeFlowNode flowNode = graph.runtimeFlowForEntrypoint(
                        f.entrypointId != null ? f.entrypointId.serialize() : "")
                .orElseThrow(() -> new IllegalStateException("flow not found in graph for ep=" + f.entrypointId));
        return new GraphResult(flowNode, graph);
    }

    private ArchitectureModel model(int n) {
        ArchitectureModel m = new ArchitectureModel("test");
        for (int i = 0; i < n; i++) {
            Component c = new Component();
            c.id = ComponentId.of("Comp" + i);
            c.name = "Comp" + i;
            c.type = i == 0 ? ComponentType.REST_RESOURCE : ComponentType.SERVICE;
            m.components.add(c);
        }
        return m;
    }

    private RuntimeFlow flow(int n) {
        RuntimeFlow f = new RuntimeFlow();
        f.id = "flow:test";
        f.entrypointId = EntrypointId.deserialize("test");
        for (int i = 0; i < n; i++) {
            RuntimeFlowStep s = new RuntimeFlowStep();
            s.order = i;
            s.componentId = ComponentId.of("Comp" + i);
            s.componentName = "Comp" + i;
            s.componentType = i == 0 ? "REST_RESOURCE" : "SERVICE";
            s.via = "call";
            f.steps.add(s);
            if (i > 0) {
                f.edges.add(
                        new RuntimeFlow.FlowEdge(ComponentId.of("Comp" + (i - 1)), ComponentId.of("Comp" + i), "call"));
            }
        }
        return f;
    }
}
