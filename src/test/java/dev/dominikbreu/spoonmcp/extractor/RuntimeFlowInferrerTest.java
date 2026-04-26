package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeFlowInferrerTest {

    private final RuntimeFlowInferrer inferrer = new RuntimeFlowInferrer();

    /**
     * Canonical flow: Resource -> Service -> Repository
     */
    @Test
    void followsInjectionDependenciesInOrder() {
        ArchitectureModel model = threeLayerModel();
        Entrypoint ep = model.entrypoints.get(0);

        RuntimeFlow flow = inferrer.infer(ep.id, 5, model);

        assertThat(flow).isNotNull();
        assertThat(flow.steps).hasSize(3);
        assertThat(flow.steps.get(0).componentName).isEqualTo("Resource");
        assertThat(flow.steps.get(1).componentName).isEqualTo("Service");
        assertThat(flow.steps.get(2).componentName).isEqualTo("Repository");
    }

    @Test
    void entrypointIdIsPreserved() {
        ArchitectureModel model = threeLayerModel();
        Entrypoint ep = model.entrypoints.get(0);

        RuntimeFlow flow = inferrer.infer(ep.id, 5, model);

        assertThat(flow.entrypointId).isEqualTo(ep.id);
    }

    @Test
    void stepsHaveCorrectOrderField() {
        ArchitectureModel model = threeLayerModel();
        RuntimeFlow flow = inferrer.infer(model.entrypoints.get(0).id, 5, model);

        for (int i = 0; i < flow.steps.size(); i++) {
            assertThat(flow.steps.get(i).order).isEqualTo(i);
        }
    }

    @Test
    void stepsHaveComponentType() {
        ArchitectureModel model = threeLayerModel();
        RuntimeFlow flow = inferrer.infer(model.entrypoints.get(0).id, 5, model);

        assertThat(flow.steps).allMatch(s -> s.componentType != null && !s.componentType.isEmpty());
    }

    @Test
    void respectsMaxDepthOfOne() {
        ArchitectureModel model = threeLayerModel();
        RuntimeFlow flow = inferrer.infer(model.entrypoints.get(0).id, 1, model);

        // depth=1: Resource (0) + Service (1) only
        assertThat(flow.steps).hasSize(2);
        assertThat(flow.steps.get(0).componentName).isEqualTo("Resource");
        assertThat(flow.steps.get(1).componentName).isEqualTo("Service");
    }

    @Test
    void respectsMaxDepthOfZero() {
        ArchitectureModel model = threeLayerModel();
        RuntimeFlow flow = inferrer.infer(model.entrypoints.get(0).id, 0, model);

        assertThat(flow.steps).hasSize(1);
        assertThat(flow.steps.get(0).componentName).isEqualTo("Resource");
    }

    @Test
    void filtersUtilityNodesFromFlow() {
        ArchitectureModel model = modelWithUtility();
        RuntimeFlow flow = inferrer.infer(model.entrypoints.get(0).id, 5, model);

        assertThat(flow.steps).noneMatch(s -> s.componentName.equals("Mapper"));
        assertThat(flow.steps).anyMatch(s -> s.componentName.equals("Resource"));
        assertThat(flow.steps).anyMatch(s -> s.componentName.equals("Service"));
    }

    @Test
    void returnsNullForUnknownEntrypoint() {
        ArchitectureModel model = threeLayerModel();
        assertThat(inferrer.infer("ep:nonexistent", 5, model)).isNull();
    }

    @Test
    void matchesByPartialEntrypointId() {
        ArchitectureModel model = threeLayerModel();
        RuntimeFlow flow = inferrer.infer("getOrder", 5, model);
        assertThat(flow).isNotNull();
    }

    @Test
    void noVisitedNodeAppearsMoreThanOnce() {
        ArchitectureModel model = threeLayerModel();
        RuntimeFlow flow = inferrer.infer(model.entrypoints.get(0).id, 5, model);

        long unique = flow.steps.stream().map(s -> s.componentId).distinct().count();
        assertThat(unique).isEqualTo(flow.steps.size());
    }

    // ── model builders ────────────────────────────────────────────────────────

    /** Resource –inject–> Service –inject–> Repository, with one GET entrypoint on Resource */
    private static ArchitectureModel threeLayerModel() {
        ArchitectureModel m = new ArchitectureModel("test");
        Component resource   = comp("Resource",   ComponentType.REST_RESOURCE);
        Component service    = comp("Service",    ComponentType.SERVICE);
        Component repository = comp("Repository", ComponentType.REPOSITORY);
        m.components.addAll(List.of(resource, service, repository));
        m.dependencies.addAll(List.of(
            dep(resource.id, service.id),
            dep(service.id, repository.id)
        ));
        m.entrypoints.add(ep("ep:Resource#getOrder", "getOrder", resource.id, "GET", "/orders/{id}"));
        return m;
    }

    /** Resource –inject–> Mapper(UTILITY) –inject–> Service */
    private static ArchitectureModel modelWithUtility() {
        ArchitectureModel m = new ArchitectureModel("test");
        Component resource = comp("Resource", ComponentType.REST_RESOURCE);
        Component mapper   = comp("Mapper",   ComponentType.UTILITY);
        Component service  = comp("Service",  ComponentType.SERVICE);
        m.components.addAll(List.of(resource, mapper, service));
        m.dependencies.addAll(List.of(dep(resource.id, mapper.id), dep(mapper.id, service.id)));
        m.entrypoints.add(ep("ep:Resource#doSomething", "doSomething", resource.id, "GET", "/items"));
        return m;
    }

    private static Component comp(String name, ComponentType type) {
        Component c = new Component();
        c.id = "comp:" + name;
        c.name = name;
        c.type = type;
        c.technology = "test";
        return c;
    }

    private static Dependency dep(String from, String to) {
        Dependency d = new Dependency();
        d.id = "dep:" + from + "->" + to;
        d.fromId = from;
        d.toId = to;
        d.kind = "injection";
        d.derivedFrom = "annotation";
        d.confidence = 0.95;
        return d;
    }

    private static Entrypoint ep(String id, String name, String compId, String method, String path) {
        Entrypoint e = new Entrypoint();
        e.id = id;
        e.name = name;
        e.componentId = compId;
        e.type = EntrypointType.REST_ENDPOINT;
        e.httpMethod = method;
        e.path = path;
        return e;
    }
}
