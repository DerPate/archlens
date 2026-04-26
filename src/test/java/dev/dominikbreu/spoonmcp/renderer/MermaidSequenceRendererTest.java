package dev.dominikbreu.spoonmcp.renderer;

import dev.dominikbreu.spoonmcp.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MermaidSequenceRendererTest {

    private final MermaidSequenceRenderer renderer = new MermaidSequenceRenderer();

    @Test
    void outputStartsWithSequenceDiagramDirective() {
        String out = renderer.render(flow(3), model(3));
        assertThat(out).startsWith("sequenceDiagram");
    }

    @Test
    void containsClientParticipant() {
        String out = renderer.render(flow(3), model(3));
        assertThat(out).contains("participant Client");
    }

    @Test
    void containsParticipantForEachFlowStep() {
        ArchitectureModel m = model(3);
        RuntimeFlow f = flow(3);
        String out = renderer.render(f, m);
        for (RuntimeFlowStep step : f.steps) {
            assertThat(out).contains(step.componentName);
        }
    }

    @Test
    void containsClientToFirstComponentCall() {
        String out = renderer.render(flow(3), model(3));
        assertThat(out).contains("Client->>").contains("Comp0");
    }

    @Test
    void containsForwardCallsBetweenConsecutiveSteps() {
        String out = renderer.render(flow(3), model(3));
        // Comp0 ->> Comp1, Comp1 ->> Comp2
        assertThat(out).contains("Comp0->>").contains("Comp1");
        assertThat(out).contains("Comp1->>").contains("Comp2");
    }

    @Test
    void containsReturnArrows() {
        String out = renderer.render(flow(3), model(3));
        assertThat(out).contains("-->>");
    }

    @Test
    void containsHttpMethodAndPathWhenRestEndpoint() {
        ArchitectureModel m = model(2);
        Entrypoint ep = new Entrypoint();
        ep.id = "ep:Comp0#doGet";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.httpMethod = "GET";
        ep.path = "/orders/{id}";
        ep.componentId = "comp:Comp0";
        m.entrypoints.add(ep);

        RuntimeFlow f = flow(2);
        f.entrypointId = ep.id;

        String out = renderer.render(f, m);
        assertThat(out).contains("GET").contains("/orders/{id}");
    }

    @Test
    void fallbackInvokeWhenNoEntrypoint() {
        ArchitectureModel m = model(2); // no entrypoints added
        String out = renderer.render(flow(2), m);
        assertThat(out).contains("invoke");
    }

    @Test
    void emptyFlowProducesNoteOutput() {
        RuntimeFlow f = new RuntimeFlow();
        f.id = "flow:empty";
        f.entrypointId = "ep:none";
        String out = renderer.render(f, new ArchitectureModel("test"));
        assertThat(out).contains("sequenceDiagram");
        assertThat(out).contains("no flow steps");
    }

    @Test
    void singleStepFlowHasNoForwardCall() {
        ArchitectureModel m = model(1);
        RuntimeFlow f = flow(1);
        String out = renderer.render(f, m);
        // Only Client ->> Comp0 and Comp0 ->> Client
        long arrows = out.lines().filter(l -> l.contains("->>")).count();
        assertThat(arrows).isEqualTo(2); // in + out
    }

    // ── level parameter ───────────────────────────────────────────────────────

    @Test
    void componentLevelSameAsDefault() {
        ArchitectureModel m = model(3);
        RuntimeFlow f = flow(3);
        assertThat(renderer.render(f, m, "component")).isEqualTo(renderer.render(f, m));
    }

    @Test
    void nullLevelFallsBackToComponent() {
        ArchitectureModel m = model(3);
        RuntimeFlow f = flow(3);
        assertThat(renderer.render(f, m, null)).isEqualTo(renderer.render(f, m));
    }

    @Test
    void containerLevelGroupsParticipantsByContainer() {
        ArchitectureModel m = modelWithContainers();
        RuntimeFlow f = flow(3);
        String out = renderer.render(f, m, "container");
        assertThat(out).startsWith("sequenceDiagram");
        // Should have fewer participants than component level (containers, not individual comps)
        long compParticipants = renderer.render(f, m, "component").lines()
            .filter(l -> l.trim().startsWith("participant")).count();
        long containerParticipants = out.lines()
            .filter(l -> l.trim().startsWith("participant")).count();
        assertThat(containerParticipants).isLessThanOrEqualTo(compParticipants);
    }

    @Test
    void systemLevelGroupsParticipantsByApp() {
        ArchitectureModel m = modelWithApp();
        RuntimeFlow f = flow(2);
        String out = renderer.render(f, m, "system");
        assertThat(out).startsWith("sequenceDiagram");
        // At system level all comps in same app → just one participant + Client
        long participants = out.lines()
            .filter(l -> l.trim().startsWith("participant")).count();
        assertThat(participants).isGreaterThanOrEqualTo(1);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ArchitectureModel modelWithContainers() {
        ArchitectureModel m = model(3);
        Container api = new Container();
        api.id = "cnt:api"; api.name = "api"; api.appId = "app:test";
        api.componentIds.add("comp:Comp0");
        Container svc = new Container();
        svc.id = "cnt:service"; svc.name = "service"; svc.appId = "app:test";
        svc.componentIds.add("comp:Comp1");
        svc.componentIds.add("comp:Comp2");
        m.containers.add(api);
        m.containers.add(svc);
        return m;
    }

    private ArchitectureModel modelWithApp() {
        ArchitectureModel m = model(2);
        AppEntry app = new AppEntry();
        app.id = "app:test";
        app.name = "test-app";
        app.technology = "quarkus";
        app.packagingType = "jar";
        app.componentIds.add("comp:Comp0");
        app.componentIds.add("comp:Comp1");
        m.applications.add(app);
        return m;
    }



    /** Build a model with n components (Comp0..CompN-1), no entrypoints. */
    private ArchitectureModel model(int n) {
        ArchitectureModel m = new ArchitectureModel("test");
        for (int i = 0; i < n; i++) {
            Component c = new Component();
            c.id = "comp:Comp" + i;
            c.name = "Comp" + i;
            c.type = i == 0 ? ComponentType.REST_RESOURCE : ComponentType.SERVICE;
            c.technology = "test";
            m.components.add(c);
        }
        return m;
    }

    /** Build a flow with n steps (Comp0..CompN-1). */
    private RuntimeFlow flow(int n) {
        RuntimeFlow f = new RuntimeFlow();
        f.id = "flow:test";
        f.entrypointId = "ep:test";
        for (int i = 0; i < n; i++) {
            RuntimeFlowStep s = new RuntimeFlowStep();
            s.order = i;
            s.componentId = "comp:Comp" + i;
            s.componentName = "Comp" + i;
            s.componentType = i == 0 ? "REST_RESOURCE" : "SERVICE";
            s.via = "injection";
            f.steps.add(s);
        }
        return f;
    }
}
