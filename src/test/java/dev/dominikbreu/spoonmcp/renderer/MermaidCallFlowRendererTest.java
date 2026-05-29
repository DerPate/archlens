package dev.dominikbreu.spoonmcp.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
import org.junit.jupiter.api.Test;

class MermaidCallFlowRendererTest {

    private final MermaidCallFlowRenderer renderer = new MermaidCallFlowRenderer();

    @Test
    void outputStartsWithFlowchartDirective() {
        assertThat(renderer.render(flow(3), model(3))).startsWith("flowchart TD");
    }

    @Test
    void containsClientNode() {
        assertThat(renderer.render(flow(3), model(3))).contains("Client([Client])");
    }

    @Test
    void containsEachComponentBySimpleName() {
        ArchitectureModel m = model(3);
        RuntimeFlow f = flow(3);
        String out = renderer.render(f, m);
        for (RuntimeFlowStep step : f.steps) {
            assertThat(out).contains(step.componentName);
        }
    }

    @Test
    void repositoryRendersAsCylinder() {
        ArchitectureModel m = model(2);
        m.components.get(1).type = ComponentType.REPOSITORY;
        String out = renderer.render(flow(2), m);
        assertThat(out).contains("[(Comp1)]");
    }

    @Test
    void httpClientRendersAsParallelogram() {
        ArchitectureModel m = model(2);
        m.components.get(1).type = ComponentType.HTTP_CLIENT;
        String out = renderer.render(flow(2), m);
        assertThat(out).contains("[/Comp1/]");
    }

    @Test
    void schedulerRendersAsStadium() {
        ArchitectureModel m = model(2);
        m.components.get(1).type = ComponentType.SCHEDULER;
        String out = renderer.render(flow(2), m);
        assertThat(out).contains("([Comp1])");
    }

    @Test
    void containsForwardEdgesWithViaLabel() {
        RuntimeFlow f = flow(3);
        f.edges.get(0).label = "processOrder";
        f.edges.get(1).label = "save";
        String out = renderer.render(f, model(3));
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
        // Comp0 fans out to both Comp1 and Comp2 — not a chain
        f.edges.add(new RuntimeFlow.FlowEdge(ComponentId.of("Comp0"), ComponentId.of("Comp1"), "doB"));
        f.edges.add(new RuntimeFlow.FlowEdge(ComponentId.of("Comp0"), ComponentId.of("Comp2"), "doC"));

        String out = renderer.render(f, m);
        assertThat(out).contains("Comp0 -->|doB| Comp1");
        assertThat(out).contains("Comp0 -->|doC| Comp2");
        assertThat(out).doesNotContain("Comp1 -->");
    }

    @Test
    void clientEdgeShowsHttpMethodAndPath() {
        ArchitectureModel m = model(2);
        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize("Comp0#create");
        ep.httpMethod = "POST";
        ep.path = "/orders";
        ep.componentId = ComponentId.of("Comp0");
        m.entrypoints.add(ep);
        RuntimeFlow f = flow(2);
        f.entrypointId = ep.id;
        String out = renderer.render(f, m);
        assertThat(out).contains("POST /orders");
    }

    @Test
    void clientEdgeShowsChannelNameForMessaging() {
        ArchitectureModel m = model(2);
        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize("Comp0#handle");
        ep.channelName = "order-events";
        ep.componentId = ComponentId.of("Comp0");
        m.entrypoints.add(ep);
        RuntimeFlow f = flow(2);
        f.entrypointId = ep.id;
        String out = renderer.render(f, m);
        assertThat(out).contains("order-events");
    }

    @Test
    void noReturnArrowsRendered() {
        String out = renderer.render(flow(4), model(4));
        assertThat(out).doesNotContain("-->>");
        assertThat(out).doesNotContain("result");
    }

    @Test
    void emptyFlowProducesFallbackNote() {
        RuntimeFlow f = new RuntimeFlow();
        f.id = "flow:empty";
        f.entrypointId = EntrypointId.deserialize("none");
        String out = renderer.render(f, new ArchitectureModel("test"));
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
            s.componentId = ComponentId.of("pkg" + i + ".Service");
            s.componentName = "Service";
            s.componentType = "SERVICE";
            s.via = "call";
            f.steps.add(s);
        }
        String out = renderer.render(f, m);
        assertThat(out).contains("Service_1");
        assertThat(out).contains("Service_2");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

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
