package dev.dominikbreu.archlens.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.model.*;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.EntrypointId;
import java.util.List;
import org.junit.jupiter.api.Test;

class MermaidUseCaseTimelineRendererTest {

    private final MermaidUseCaseTimelineRenderer renderer = new MermaidUseCaseTimelineRenderer();

    @Test
    void outputStartsWithGanttDirective() {
        var r = build("ep1", 3);
        assertThat(renderer.render(r.flows(), r.graph(), 5)).startsWith("gantt");
    }

    @Test
    void containsDateFormatAndAxisFormat() {
        var r = build("ep1", 3);
        String out = renderer.render(r.flows(), r.graph(), 5);
        assertThat(out).contains("dateFormat  X");
        assertThat(out).contains("axisFormat  step %s");
    }

    @Test
    void containsSectionForEachFlow() {
        var r1 = build("ep1", 2);
        var r2 = build("ep2", 2);
        List<GraphQuery.RuntimeFlowNode> flows = List.of(r1.flows().get(0), r2.flows().get(0));
        // Both graphs use separate models; use r1.graph() which has ep1's flow
        // To combine, build a model with both flows
        ArchitectureModel m = new ArchitectureModel("test");
        m.runtimeFlows.add(flow("ep1", 2));
        m.runtimeFlows.add(flow("ep2", 2));
        GraphQuery graph = GraphQuery.from(m);
        List<GraphQuery.RuntimeFlowNode> allFlows = graph.allRuntimeFlows();
        String out = renderer.render(allFlows, graph, 5);
        assertThat(out.lines().filter(l -> l.trim().startsWith("section")).count()).isEqualTo(2);
    }

    @Test
    void sectionLabelUsesHttpMethodAndPath() {
        ArchitectureModel m = new ArchitectureModel("test");
        Entrypoint ep = ep("create", "POST", "/orders", null, "Comp0");
        m.entrypoints.add(ep);
        m.runtimeFlows.add(flow("create", 2));
        GraphQuery graph = GraphQuery.from(m);
        String out = renderer.render(graph.allRuntimeFlows(), graph, 5);
        assertThat(out).contains("POST /orders");
    }

    @Test
    void sectionLabelUsesChannelNameForMessaging() {
        ArchitectureModel m = new ArchitectureModel("test");
        Entrypoint ep = ep("msg", null, null, "order-events", "Comp0");
        m.entrypoints.add(ep);
        m.runtimeFlows.add(flow("msg", 2));
        GraphQuery graph = GraphQuery.from(m);
        String out = renderer.render(graph.allRuntimeFlows(), graph, 5);
        assertThat(out).contains("order-events");
    }

    @Test
    void firstStepIsMarkedActive() {
        var r = build("ep1", 3);
        assertThat(renderer.render(r.flows(), r.graph(), 5)).contains(":active,");
    }

    @Test
    void stepCountRespectMaxDepth() {
        var r = build("ep1", 6);
        String out = renderer.render(r.flows(), r.graph(), 3);
        long taskLines = out.lines()
                .filter(l -> l.contains(":active,") || (l.contains(":") && l.contains(", 1")))
                .count();
        assertThat(taskLines).isEqualTo(4); // 3 steps + 1 overflow
    }

    @Test
    void taskLabelContainsComponentNameAndVia() {
        ArchitectureModel m = new ArchitectureModel("test");
        Component comp = new Component();
        comp.id = ComponentId.of("Comp1");
        comp.name = "OrderService";
        comp.type = ComponentType.SERVICE;
        m.components.add(comp);
        RuntimeFlow f = flow("ep1", 2);
        f.steps.get(1).componentId = ComponentId.of("Comp1");
        f.steps.get(1).via = "processOrder";
        m.runtimeFlows.add(f);
        GraphQuery graph = GraphQuery.from(m);
        String out = renderer.render(graph.allRuntimeFlows(), graph, 5);
        assertThat(out).contains("OrderService.processOrder");
    }

    @Test
    void emptyFlowsProducesFallback() {
        String out = renderer.render(List.of(), null, 5);
        assertThat(out).contains("gantt");
        assertThat(out).contains("no use cases");
    }

    @Test
    void colonInSectionLabelIsSanitized() {
        ArchitectureModel m = new ArchitectureModel("test");
        Entrypoint ep = ep("sched", null, null, null, "Comp0");
        ep.name = "Scheduled: cleanup";
        m.entrypoints.add(ep);
        m.runtimeFlows.add(flow("sched", 1));
        GraphQuery graph = GraphQuery.from(m);
        String out = renderer.render(graph.allRuntimeFlows(), graph, 5);
        String sectionLine = out.lines()
                .filter(l -> l.trim().startsWith("section"))
                .findFirst()
                .orElse("");
        assertThat(sectionLine).doesNotContain("Scheduled:");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private record GraphResult(List<GraphQuery.RuntimeFlowNode> flows, GraphQuery graph) {}

    private GraphResult build(String epId, int steps) {
        ArchitectureModel m = new ArchitectureModel("test");
        m.runtimeFlows.add(flow(epId, steps));
        GraphQuery graph = GraphQuery.from(m);
        return new GraphResult(graph.allRuntimeFlows(), graph);
    }

    private RuntimeFlow flow(String entrypointId, int steps) {
        RuntimeFlow f = new RuntimeFlow();
        f.id = "flow:" + entrypointId;
        f.entrypointId = EntrypointId.deserialize(entrypointId);
        for (int i = 0; i < steps; i++) {
            RuntimeFlowStep s = new RuntimeFlowStep();
            s.componentId = ComponentId.of("Comp" + i);
            s.componentName = "Comp" + i;
            s.componentType = "SERVICE";
            s.via = "method" + i;
            f.steps.add(s);
        }
        return f;
    }

    private Entrypoint ep(String id, String httpMethod, String path, String channelName, String componentId) {
        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize(id);
        ep.httpMethod = httpMethod;
        ep.path = path;
        ep.channelName = channelName;
        ep.componentId = ComponentId.of(componentId);
        return ep;
    }
}
