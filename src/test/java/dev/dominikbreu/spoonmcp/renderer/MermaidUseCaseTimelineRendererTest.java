package dev.dominikbreu.spoonmcp.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
import java.util.List;
import org.junit.jupiter.api.Test;

class MermaidUseCaseTimelineRendererTest {

    private final MermaidUseCaseTimelineRenderer renderer = new MermaidUseCaseTimelineRenderer();

    @Test
    void outputStartsWithGanttDirective() {
        assertThat(renderer.render(List.of(flow("ep1", 3)), model(), 5)).startsWith("gantt");
    }

    @Test
    void containsDateFormatAndAxisFormat() {
        String out = renderer.render(List.of(flow("ep1", 3)), model(), 5);
        assertThat(out).contains("dateFormat  X");
        assertThat(out).contains("axisFormat  step %s");
    }

    @Test
    void containsSectionForEachFlow() {
        List<RuntimeFlow> flows = List.of(flow("ep1", 2), flow("ep2", 2));
        String out = renderer.render(flows, model(), 5);
        assertThat(out.lines().filter(l -> l.trim().startsWith("section")).count())
                .isEqualTo(2);
    }

    @Test
    void sectionLabelUsesHttpMethodAndPath() {
        ArchitectureModel m = model();
        Entrypoint ep = ep("create", "POST", "/orders", null, "Comp0");
        m.entrypoints.add(ep);
        RuntimeFlow f = flow("create", 2);
        String out = renderer.render(List.of(f), m, 5);
        assertThat(out).contains("POST /orders");
    }

    @Test
    void sectionLabelUsesChannelNameForMessaging() {
        ArchitectureModel m = model();
        Entrypoint ep = ep("msg", null, null, "order-events", "Comp0");
        m.entrypoints.add(ep);
        RuntimeFlow f = flow("msg", 2);
        String out = renderer.render(List.of(f), m, 5);
        assertThat(out).contains("order-events");
    }

    @Test
    void firstStepIsMarkedActive() {
        String out = renderer.render(List.of(flow("ep1", 3)), model(), 5);
        assertThat(out).contains(":active,");
    }

    @Test
    void stepCountRespectMaxDepth() {
        RuntimeFlow f = flow("ep1", 6);
        String out = renderer.render(List.of(f), model(), 3);
        // Only 3 steps rendered as tasks + 1 overflow line
        long taskLines = out.lines()
                .filter(l -> l.contains(":active,") || (l.contains(":") && l.contains(", 1")))
                .count();
        assertThat(taskLines).isEqualTo(4); // 3 steps + 1 overflow
    }

    @Test
    void taskLabelContainsComponentNameAndVia() {
        RuntimeFlow f = flow("ep1", 2);
        f.steps.get(1).componentName = "OrderService";
        f.steps.get(1).via = "processOrder";
        String out = renderer.render(List.of(f), model(), 5);
        assertThat(out).contains("OrderService.processOrder");
    }

    @Test
    void emptyFlowsProducesFallback() {
        String out = renderer.render(List.of(), model(), 5);
        assertThat(out).contains("gantt");
        assertThat(out).contains("no use cases");
    }

    @Test
    void colonInSectionLabelIsSanitized() {
        ArchitectureModel m = model();
        Entrypoint ep = ep("sched", null, null, null, "Comp0");
        ep.name = "Scheduled: cleanup";
        m.entrypoints.add(ep);
        RuntimeFlow f = flow("sched", 1);
        String out = renderer.render(List.of(f), m, 5);
        // Section label must not contain a colon (Mermaid syntax restriction)
        String sectionLine = out.lines()
                .filter(l -> l.trim().startsWith("section"))
                .findFirst()
                .orElse("");
        assertThat(sectionLine).doesNotContain("Scheduled:");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ArchitectureModel model() {
        return new ArchitectureModel("test");
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
