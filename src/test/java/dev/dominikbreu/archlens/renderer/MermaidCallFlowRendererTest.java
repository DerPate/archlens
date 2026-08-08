package dev.dominikbreu.archlens.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.model.*;
import dev.dominikbreu.archlens.model.ids.AppId;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.EntrypointId;
import org.junit.jupiter.api.Test;

class MermaidCallFlowRendererTest {

    private final MermaidCallFlowRenderer renderer = new MermaidCallFlowRenderer();

    @Test
    void outputStartsWithThemeHeaderAndSequenceDirective() {
        var r = buildGraph(model(3), flow(3));
        String out = renderer.render(r.flowNode(), r.graph());
        assertThat(out).startsWith("%%{init:");
        assertThat(out).contains("sequenceDiagram\n");
        assertThat(out).contains("autonumber");
    }

    @Test
    void containsClientActor() {
        var r = buildGraph(model(3), flow(3));
        assertThat(renderer.render(r.flowNode(), r.graph())).contains("actor Client");
    }

    @Test
    void declaresParticipantsWithStereotypesInFirstAppearanceOrder() {
        var r = buildGraph(model(3), flow(3));
        String out = renderer.render(r.flowNode(), r.graph());
        assertThat(out).contains("participant Comp0 as Comp0«rest_resource»");
        assertThat(out).contains("participant Comp1 as Comp1«service»");
        assertThat(out.indexOf("participant Comp0")).isLessThan(out.indexOf("participant Comp1"));
    }

    @Test
    void repositoryGetsRepositoryStereotype() {
        ArchitectureModel m = model(2);
        m.components.get(1).type = ComponentType.REPOSITORY;
        var r = buildGraph(m, flow(2));
        assertThat(renderer.render(r.flowNode(), r.graph())).contains("Comp1«repository»");
    }

    @Test
    void clientEdgeShowsHttpMethodAndPathAndActivatesFirstParticipant() {
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
        assertThat(renderer.render(r.flowNode(), r.graph())).contains("Client->>+Comp0: POST /orders");
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
        assertThat(renderer.render(r.flowNode(), r.graph())).contains("Client->>+Comp0: order-events");
    }

    @Test
    void syncCallsUseSolidArrowWithLabel() {
        RuntimeFlow f = flow(3);
        f.edges.get(0).label = "processOrder";
        var r = buildGraph(model(3), f);
        assertThat(renderer.render(r.flowNode(), r.graph())).contains("Comp0->>+Comp1: processOrder");
    }

    @Test
    void messagingTargetUsesAsyncArrow() {
        ArchitectureModel m = model(2);
        m.components.get(1).type = ComponentType.MESSAGE_DRIVEN_BEAN;
        var r = buildGraph(m, flow(2));
        assertThat(renderer.render(r.flowNode(), r.graph())).contains("Comp0-->>+Comp1: call");
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
        assertThat(out).contains("Comp0->>+Comp1: doB");
        assertThat(out).contains("Comp0->>+Comp2: doC");
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
        assertThat(out).doesNotContain("Comp0->>Comp0");
        assertThat(out).doesNotContain("Comp0->>+Comp0");
        assertThat(out).contains(": process");
    }

    @Test
    void activatedParticipantsAreDeactivatedInReverseOrder() {
        var r = buildGraph(model(3), flow(3));
        String out = renderer.render(r.flowNode(), r.graph());
        int d2 = out.indexOf("deactivate Comp2");
        int d1 = out.indexOf("deactivate Comp1");
        int d0 = out.indexOf("deactivate Comp0");
        assertThat(d2).isPositive();
        assertThat(d2).isLessThan(d1);
        assertThat(d1).isLessThan(d0);
    }

    @Test
    void emptyFlowProducesFallbackNote() {
        String out = renderer.render(null, null);
        assertThat(out).contains("sequenceDiagram");
        assertThat(out).contains("no flow steps");
    }

    @Test
    void multiAppFlowGroupsParticipantsInBoxes() {
        ArchitectureModel m = model(2);
        AppEntry orders = new AppEntry();
        orders.id = AppId.of("app:orders");
        orders.name = "orders";
        orders.technology = "quarkus";
        orders.packagingType = "jar";
        orders.componentIds.add(ComponentId.of("Comp0"));
        m.applications.add(orders);
        AppEntry billing = new AppEntry();
        billing.id = AppId.of("app:billing");
        billing.name = "billing";
        billing.technology = "quarkus";
        billing.packagingType = "jar";
        billing.componentIds.add(ComponentId.of("Comp1"));
        m.applications.add(billing);
        m.components.get(0).module = AppId.of("app:orders");
        m.components.get(1).module = AppId.of("app:billing");

        var r = buildGraph(m, flow(2));
        String out = renderer.render(r.flowNode(), r.graph());
        assertThat(out).contains("box orders");
        assertThat(out).contains("box billing");
        assertThat(out.lines().filter(l -> "end".equals(l.trim())).count()).isEqualTo(2);
    }

    @Test
    void singleAppFlowUsesNoBoxes() {
        var r = buildGraph(model(3), flow(3));
        assertThat(renderer.render(r.flowNode(), r.graph())).doesNotContain("box ");
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
        f.edges.add(new RuntimeFlow.FlowEdge(ComponentId.of("pkg0.Service"), ComponentId.of("pkg1.Service"), "call"));
        var r = buildGraph(m, f);
        String out = renderer.render(r.flowNode(), r.graph());
        assertThat(out).contains("participant Service_1");
        assertThat(out).contains("participant Service_2");
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
