package dev.dominikbreu.archlens.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.*;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.DependencyId;
import dev.dominikbreu.archlens.model.ids.EntrypointId;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeFlowInferrerTest {

    private final RuntimeFlowInferrer inferrer = new RuntimeFlowInferrer();

    /**
     * Canonical flow: Resource -> Service -> Repository
     */
    @Test
    void followsInjectionDependenciesInOrder() {
        ArchitectureModel model = threeLayerModel();
        Entrypoint ep = model.entrypoints.get(0);

        RuntimeFlow flow = inferrer.infer(ep.id.serialize(), 5, model);

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

        RuntimeFlow flow = inferrer.infer(ep.id.serialize(), 5, model);

        assertThat(flow.entrypointId).isEqualTo(ep.id);
    }

    @Test
    void acceptsPrebuiltModelIndex() {
        ArchitectureModel model = threeLayerModel();
        Entrypoint ep = model.entrypoints.get(0);
        ModelIndex index = ModelIndex.build(model);

        RuntimeFlow flow = inferrer.infer(ep.id.serialize(), 5, model, index);

        assertThat(flow).isNotNull();
        assertThat(flow.entrypointId).isEqualTo(ep.id);
    }

    @Test
    void stepsHaveCorrectOrderField() {
        ArchitectureModel model = threeLayerModel();
        RuntimeFlow flow = inferrer.infer(model.entrypoints.get(0).id.serialize(), 5, model);

        for (int i = 0; i < flow.steps.size(); i++) {
            assertThat(flow.steps.get(i).order).isEqualTo(i);
        }
    }

    @Test
    void stepsHaveComponentType() {
        ArchitectureModel model = threeLayerModel();
        RuntimeFlow flow = inferrer.infer(model.entrypoints.get(0).id.serialize(), 5, model);

        assertThat(flow.steps).isNotEmpty().allMatch(s -> s.componentType != null && !s.componentType.isEmpty());
    }

    @Test
    void respectsMaxDepthOfOne() {
        ArchitectureModel model = threeLayerModel();
        RuntimeFlow flow = inferrer.infer(model.entrypoints.get(0).id.serialize(), 1, model);

        // depth=1: Resource (0) + Service (1) only
        assertThat(flow.steps).hasSize(2);
        assertThat(flow.steps.get(0).componentName).isEqualTo("Resource");
        assertThat(flow.steps.get(1).componentName).isEqualTo("Service");
    }

    @Test
    void respectsMaxDepthOfZero() {
        ArchitectureModel model = threeLayerModel();
        RuntimeFlow flow = inferrer.infer(model.entrypoints.get(0).id.serialize(), 0, model);

        assertThat(flow.steps).hasSize(1);
        assertThat(flow.steps.get(0).componentName).isEqualTo("Resource");
    }

    @Test
    void filtersUtilityNodesFromFlow() {
        ArchitectureModel model = modelWithUtility();
        RuntimeFlow flow = inferrer.infer(model.entrypoints.get(0).id.serialize(), 5, model);

        assertThat(flow.steps).noneMatch(s -> "Mapper".equals(s.componentName));
        assertThat(flow.steps).anyMatch(s -> "Resource".equals(s.componentName));
        assertThat(flow.steps).anyMatch(s -> "Service".equals(s.componentName));
    }

    @Test
    void step0ViaUsesEntrypointHttpMethodAndPath() {
        ArchitectureModel model = threeLayerModel();
        RuntimeFlow flow = inferrer.infer(model.entrypoints.get(0).id.serialize(), 5, model);

        assertThat(flow.steps.get(0).via).isEqualTo("GET /orders/{id}");
    }

    @Test
    void step0ViaUsesChannelNameForMessagingEntrypoint() {
        ArchitectureModel m = new ArchitectureModel("test");
        Component resource = comp("Processor", ComponentType.SERVICE);
        m.components.add(resource);
        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize("Processor#onMessage");
        ep.name = "onMessage";
        ep.componentId = resource.id;
        ep.type = EntrypointType.MESSAGING_CONSUMER;
        ep.channelName = "orders-in";
        m.entrypoints.add(ep);

        RuntimeFlow flow = inferrer.infer(ep.id.serialize(), 5, m);

        assertThat(flow.steps.get(0).via).isEqualTo("orders-in");
    }

    @Test
    void subsequentStepsUseActualDepKind() {
        ArchitectureModel m = new ArchitectureModel("test");
        Component producer = comp("Producer", ComponentType.CDI_EVENT_PRODUCER);
        Component consumer = comp("Consumer", ComponentType.CDI_EVENT_CONSUMER);
        m.components.addAll(List.of(producer, consumer));
        Dependency d = new Dependency();
        d.fromId = producer.id;
        d.toId = consumer.id;
        d.id = DependencyId.of(d.fromId, d.toId);
        d.kind = "cdi-event";
        d.confidence = 0.8;
        m.dependencies.add(d);
        m.entrypoints.add(ep("Producer#fire", "fire", producer.id, null, null));

        RuntimeFlow flow = inferrer.infer(m.entrypoints.get(0).id.serialize(), 5, m);

        assertThat(flow.steps).hasSize(2);
        assertThat(flow.steps.get(1).via).isEqualTo("cdi-event");
    }

    @Test
    void rawMessagingClientIsExcludedFromFlow() {
        // MQTTConsumer (messaging entrypoint) injects VertxMqttClient (HTTP_CLIENT + "messaging" stereotype).
        // The client is transport infrastructure; it must not appear as a called dependency in the flow.
        ArchitectureModel m = new ArchitectureModel("test");
        Component consumer = comp("MQTTConsumer", ComponentType.SERVICE);
        Component mqttClient = comp("VertxMqttClient", ComponentType.HTTP_CLIENT);
        mqttClient.stereotypes = new java.util.ArrayList<>(List.of("client", "messaging"));
        Component service = comp("OrderService", ComponentType.SERVICE);
        m.components.addAll(List.of(consumer, mqttClient, service));
        m.dependencies.addAll(List.of(dep(consumer.id, mqttClient.id), dep(consumer.id, service.id)));
        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize("MQTTConsumer#handle");
        ep.name = "handle";
        ep.componentId = consumer.id;
        ep.type = EntrypointType.MESSAGING_CONSUMER;
        ep.channelName = "device-events";
        m.entrypoints.add(ep);

        RuntimeFlow flow = inferrer.infer(ep.id.serialize(), 5, m);

        assertThat(flow.steps).noneMatch(s -> "VertxMqttClient".equals(s.componentName));
        assertThat(flow.steps).anyMatch(s -> "MQTTConsumer".equals(s.componentName));
        assertThat(flow.steps).anyMatch(s -> "OrderService".equals(s.componentName));
    }

    @Test
    void returnsNullForUnknownEntrypoint() {
        ArchitectureModel model = threeLayerModel();
        assertThat(inferrer.infer("nonexistent", 5, model)).isNull();
    }

    @Test
    void matchesByPartialEntrypointId() {
        ArchitectureModel model = threeLayerModel();
        RuntimeFlow flow = inferrer.infer("getOrder", 5, model);
        assertThat(flow).isNotNull();
    }

    // ── extractMethodFromRef / extractPathFromRef ─────────────────────────────

    @Test
    void extractMethodFromRef_parsesUppercaseMethod() {
        assertThat(RuntimeFlowInferrer.extractMethodFromRef("GET /account")).isEqualTo("GET");
        assertThat(RuntimeFlowInferrer.extractMethodFromRef("POST /account")).isEqualTo("POST");
        assertThat(RuntimeFlowInferrer.extractMethodFromRef("DELETE /account/{id}"))
                .isEqualTo("DELETE");
        assertThat(RuntimeFlowInferrer.extractMethodFromRef("PATCH /customer/{id}/address/{aid}"))
                .isEqualTo("PATCH");
    }

    @Test
    void extractMethodFromRef_isCaseInsensitive() {
        assertThat(RuntimeFlowInferrer.extractMethodFromRef("get /account")).isEqualTo("GET");
        assertThat(RuntimeFlowInferrer.extractMethodFromRef("Post /account")).isEqualTo("POST");
    }

    @Test
    void extractMethodFromRef_returnsNullForPlainPath() {
        assertThat(RuntimeFlowInferrer.extractMethodFromRef("/account")).isNull();
        assertThat(RuntimeFlowInferrer.extractMethodFromRef("getAll")).isNull();
        assertThat(RuntimeFlowInferrer.extractMethodFromRef("GET")).isNull(); // no space+slash
    }

    @Test
    void extractPathFromRef_stripsMethodPrefix() {
        assertThat(RuntimeFlowInferrer.extractPathFromRef("GET /account")).isEqualTo("/account");
        assertThat(RuntimeFlowInferrer.extractPathFromRef("POST /customer/{id}"))
                .isEqualTo("/customer/{id}");
    }

    @Test
    void extractPathFromRef_returnsOriginalWhenNoMethod() {
        assertThat(RuntimeFlowInferrer.extractPathFromRef("/account")).isEqualTo("/account");
        assertThat(RuntimeFlowInferrer.extractPathFromRef("getAll")).isEqualTo("getAll");
    }

    // ── findEntrypoint — HTTP-method disambiguation ───────────────────────────

    @Test
    void findEntrypoint_byMethodAndPath_disambiguatesGetFromPost() {
        ArchitectureModel m = new ArchitectureModel("test");
        Component ctrl = comp("AccountController", ComponentType.REST_RESOURCE);
        m.components.add(ctrl);
        Entrypoint post = ep("AccountController#add:POST", "add", ctrl.id, "POST", "/account");
        Entrypoint get = ep("AccountController#getAll:GET", "getAll", ctrl.id, "GET", "/account");
        m.entrypoints.add(post); // POST registered first
        m.entrypoints.add(get);

        assertThat(inferrer.findEntrypoint("GET /account", m).id).isEqualTo(get.id);
        assertThat(inferrer.findEntrypoint("POST /account", m).id).isEqualTo(post.id);
    }

    @Test
    void findEntrypoint_plainPathFallsBackToFirstMatch() {
        // Without a method prefix the first registered endpoint is returned (existing behaviour).
        ArchitectureModel m = new ArchitectureModel("test");
        Component ctrl = comp("AccountController", ComponentType.REST_RESOURCE);
        m.components.add(ctrl);
        Entrypoint post = ep("AccountController#add:POST", "add", ctrl.id, "POST", "/account");
        Entrypoint get = ep("AccountController#getAll:GET", "getAll", ctrl.id, "GET", "/account");
        m.entrypoints.add(post);
        m.entrypoints.add(get);

        assertThat(inferrer.findEntrypoint("/account", m).id).isEqualTo(post.id);
    }

    @Test
    void matchesByExactPath() {
        ArchitectureModel model = threeLayerModel();
        RuntimeFlow flow = inferrer.infer("/orders/{id}", 5, model);
        assertThat(flow).isNotNull();
        assertThat(flow.entrypointId).isEqualTo(model.entrypoints.get(0).id);
    }

    @Test
    void matchesByPathPrefix() {
        // ref "/orders" should match "/orders/{id}"
        ArchitectureModel model = threeLayerModel();
        RuntimeFlow flow = inferrer.infer("/orders", 5, model);
        assertThat(flow).isNotNull();
        assertThat(flow.entrypointId).isEqualTo(model.entrypoints.get(0).id);
    }

    @Test
    void exactPathWinsOverPrefixMatchRegardlessOfIterationOrder() {
        // When both an exact /customer endpoint and a /customer/{id}/sub endpoint exist
        // and the sub-path comes first in the model, the exact match must still be returned.
        ArchitectureModel m = new ArchitectureModel("test");
        Component ctrl = comp("CustomerController", ComponentType.REST_RESOURCE);
        m.components.add(ctrl);
        Entrypoint sub = new Entrypoint();
        sub.id = EntrypointId.deserialize("CustomerController#addContact:POST");
        sub.name = "addContact";
        sub.componentId = ctrl.id;
        sub.type = EntrypointType.REST_ENDPOINT;
        sub.path = "/customer/{id}/contactPerson";
        sub.httpMethod = "POST";
        m.entrypoints.add(sub); // added first so it appears before the exact match
        Entrypoint exact = new Entrypoint();
        exact.id = EntrypointId.deserialize("CustomerController#getAll:GET");
        exact.name = "getAll";
        exact.componentId = ctrl.id;
        exact.type = EntrypointType.REST_ENDPOINT;
        exact.path = "/customer";
        exact.httpMethod = "GET";
        m.entrypoints.add(exact);

        Entrypoint found = inferrer.findEntrypoint("/customer", m);
        assertThat(found).isNotNull();
        assertThat(found.id).isEqualTo(exact.id);
    }

    @Test
    void doesNotMatchSubPathWhenRefIsParameterized() {
        // /absence/{id} must NOT match /absence/{id}/cancel — ref already contains a
        // path variable so only an exact match is valid.
        assertThat(RuntimeFlowInferrer.pathPrefixMatches("/absence/{id}/cancel", "/absence/{id}"))
                .isFalse();
        assertThat(RuntimeFlowInferrer.pathPrefixMatches("/absence/{id}", "/absence/{id}"))
                .isTrue();
    }

    @Test
    void doesNotMatchLongerPathWithMultiplePathVariables() {
        // GET /customer/{customerId}/address/{addressId} must NOT match
        // PATCH /customer/{customerId}/address/{addressId}/update even though
        // the latter starts with the former.  Multiple {id} segments must all be
        // considered "specific" and trigger exact-only matching.
        assertThat(RuntimeFlowInferrer.pathPrefixMatches(
                        "/customer/{customerId}/address/{addressId}/update",
                        "/customer/{customerId}/address/{addressId}"))
                .isFalse();
        // …and the exact path must still match itself.
        assertThat(RuntimeFlowInferrer.pathPrefixMatches(
                        "/customer/{customerId}/address/{addressId}", "/customer/{customerId}/address/{addressId}"))
                .isTrue();
    }

    @Test
    void findEntrypointPrefersExactMatchForMultiSegmentParameterizedPath() {
        // Model has two endpoints sharing the same parameterised prefix:
        //   GET  /customer/{customerId}/address/{addressId}
        //   PATCH /customer/{customerId}/address/{addressId}/update
        // When the caller passes the GET path, only the GET endpoint must be returned.
        ArchitectureModel m = new ArchitectureModel("test");
        Component ctrl = comp("CustomerController", ComponentType.REST_RESOURCE);
        m.components.add(ctrl);

        Entrypoint patch = new Entrypoint();
        patch.id = EntrypointId.deserialize("CustomerController#updateAddress:PATCH");
        patch.name = "updateAddress";
        patch.componentId = ctrl.id;
        patch.type = EntrypointType.REST_ENDPOINT;
        patch.path = "/customer/{customerId}/address/{addressId}/update";
        patch.httpMethod = "PATCH";
        m.entrypoints.add(patch); // comes before the GET in iteration order

        Entrypoint get = new Entrypoint();
        get.id = EntrypointId.deserialize("CustomerController#getAddress:GET");
        get.name = "getAddress";
        get.componentId = ctrl.id;
        get.type = EntrypointType.REST_ENDPOINT;
        get.path = "/customer/{customerId}/address/{addressId}";
        get.httpMethod = "GET";
        m.entrypoints.add(get);

        Entrypoint found = inferrer.findEntrypoint("/customer/{customerId}/address/{addressId}", m);
        assertThat(found).isNotNull();
        assertThat(found.id).isEqualTo(get.id);
    }

    @Test
    void doesNotMatchPathSubstring() {
        // "/budgetControl/orders/{id}" must NOT be matched for ref "/orders"
        ArchitectureModel model = threeLayerModel();
        Component ctrl = comp("OtherController", ComponentType.REST_RESOURCE);
        model.components.add(ctrl);
        Entrypoint nested = new Entrypoint();
        nested.id = EntrypointId.deserialize("OtherController#get");
        nested.name = "get";
        nested.componentId = ctrl.id;
        nested.type = EntrypointType.REST_ENDPOINT;
        nested.path = "/budgetControl/orders/{id}";
        nested.httpMethod = "GET";
        model.entrypoints.add(nested);

        // The first entrypoint (path "/orders/{id}") must still be the match
        RuntimeFlow flow = inferrer.infer("/orders", 5, model);
        assertThat(flow).isNotNull();
        assertThat(flow.entrypointId).isEqualTo(model.entrypoints.get(0).id);
    }

    @Test
    void noVisitedNodeAppearsMoreThanOnce() {
        ArchitectureModel model = threeLayerModel();
        RuntimeFlow flow = inferrer.infer(model.entrypoints.get(0).id.serialize(), 5, model);

        long unique = flow.steps.stream().map(s -> s.componentId).distinct().count();
        assertThat(unique).isEqualTo(flow.steps.size());
    }

    @Test
    void runtimeFlowDoesNotTraverseAcrossMessagingBoundaryAsInlineCall() {
        ArchitectureModel model = new ArchitectureModel("test");
        Component scheduler = comp("Scheduler", ComponentType.SCHEDULER);
        Component consumer = comp("Consumer", ComponentType.SERVICE);
        model.components.addAll(List.of(scheduler, consumer));

        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize("Scheduler#tick");
        ep.name = "tick";
        ep.componentId = scheduler.id;
        ep.type = EntrypointType.SCHEDULER;
        model.entrypoints.add(ep);

        CallEdge edge = new CallEdge();
        edge.fromComponentId = scheduler.id;
        edge.fromMethod = "tick";
        edge.toComponentId = consumer.id;
        edge.toMethod = "process";
        edge.callKind = "messaging";
        model.callEdges.add(edge);

        RuntimeFlow flow = inferrer.infer(ep.id.serialize(), 5, model);

        assertThat(flow.steps).extracting(step -> step.componentName).contains("Scheduler");
        assertThat(flow.steps).extracting(step -> step.componentName).doesNotContain("Consumer");
    }

    @Test
    void runtimeFlowTraversesThroughHiddenInlineComponents() {
        ArchitectureModel model = new ArchitectureModel("test");
        Component resource = comp("Resource", ComponentType.REST_RESOURCE);
        Component mapper = comp("Mapper", ComponentType.UTILITY);
        Component service = comp("Service", ComponentType.SERVICE);
        model.components.addAll(List.of(resource, mapper, service));
        model.entrypoints.add(ep("Resource#get", "get", resource.id, "GET", "/orders"));

        CallEdge toMapper = new CallEdge();
        toMapper.fromComponentId = resource.id;
        toMapper.fromMethod = "get";
        toMapper.toComponentId = mapper.id;
        toMapper.toMethod = "map";
        toMapper.callKind = "direct";
        model.callEdges.add(toMapper);

        CallEdge toService = new CallEdge();
        toService.fromComponentId = mapper.id;
        toService.fromMethod = "map";
        toService.toComponentId = service.id;
        toService.toMethod = "handle";
        toService.callKind = "direct";
        model.callEdges.add(toService);

        RuntimeFlow flow = inferrer.infer(model.entrypoints.get(0).id.serialize(), 5, model);

        assertThat(flow.steps).extracting(step -> step.componentName).contains("Resource", "Service");
        assertThat(flow.steps).extracting(step -> step.componentName).doesNotContain("Mapper");
    }

    @Test
    void runtimeFlowKeepsUnknownPlainJavaComponentsVisible() {
        ArchitectureModel model = new ArchitectureModel("test");
        Component main = comp("Main", ComponentType.UNKNOWN);
        Component game = comp("Game", ComponentType.UNKNOWN);
        model.components.addAll(List.of(main, game));
        model.dependencies.add(dep(main.id, game.id));

        Entrypoint ep = ep("Main#main", "main", main.id, null, null);
        ep.type = EntrypointType.MAIN_METHOD;
        model.entrypoints.add(ep);

        RuntimeFlow flow = inferrer.infer(ep.id.serialize(), 5, model);

        assertThat(flow.steps).extracting(step -> step.componentName).containsExactly("Main", "Game");
    }

    @Test
    void bothCallersToSharedComponentProduceEdges() {
        // Regression: when RandomPlayer and SimplePlayer both call Strategy,
        // only RandomPlayer->Strategy was recorded; SimplePlayer->Strategy was dropped
        // because Strategy's DFS key was already marked visited.
        ArchitectureModel model = new ArchitectureModel("test");
        Component game = comp("Game", ComponentType.UNKNOWN);
        Component random = comp("RandomPlayer", ComponentType.UNKNOWN);
        Component simple = comp("SimplePlayer", ComponentType.UNKNOWN);
        Component strategy = comp("Strategy", ComponentType.UNKNOWN);
        model.components.addAll(List.of(game, random, simple, strategy));

        model.entrypoints.add(ep("Game#run", "run", game.id, null, null));

        model.callEdges.addAll(List.of(
                callEdge(game.id, "run", random.id, "nextMove"),
                callEdge(game.id, "run", simple.id, "nextMove"),
                callEdge(random.id, "nextMove", strategy.id, "nextSign"),
                callEdge(simple.id, "nextMove", strategy.id, "nextSign")));

        RuntimeFlow flow = inferrer.infer(model.entrypoints.get(0).id.serialize(), 10, model);

        assertThat(flow.edges).anySatisfy(e -> {
            assertThat(e.fromId).isEqualTo(random.id);
            assertThat(e.toId).isEqualTo(strategy.id);
        });
        assertThat(flow.edges).anySatisfy(e -> {
            assertThat(e.fromId).isEqualTo(simple.id);
            assertThat(e.toId).isEqualTo(strategy.id);
        });
    }

    // ── model builders ────────────────────────────────────────────────────────

    /** Resource –inject–> Service –inject–> Repository, with one GET entrypoint on Resource */
    private static ArchitectureModel threeLayerModel() {
        ArchitectureModel m = new ArchitectureModel("test");
        Component resource = comp("Resource", ComponentType.REST_RESOURCE);
        Component service = comp("Service", ComponentType.SERVICE);
        Component repository = comp("Repository", ComponentType.REPOSITORY);
        m.components.addAll(List.of(resource, service, repository));
        m.dependencies.addAll(List.of(dep(resource.id, service.id), dep(service.id, repository.id)));
        m.entrypoints.add(ep("Resource#getOrder", "getOrder", resource.id, "GET", "/orders/{id}"));
        return m;
    }

    /** Resource –inject–> Mapper(UTILITY) –inject–> Service */
    private static ArchitectureModel modelWithUtility() {
        ArchitectureModel m = new ArchitectureModel("test");
        Component resource = comp("Resource", ComponentType.REST_RESOURCE);
        Component mapper = comp("Mapper", ComponentType.UTILITY);
        Component service = comp("Service", ComponentType.SERVICE);
        m.components.addAll(List.of(resource, mapper, service));
        m.dependencies.addAll(List.of(dep(resource.id, mapper.id), dep(mapper.id, service.id)));
        m.entrypoints.add(ep("Resource#doSomething", "doSomething", resource.id, "GET", "/items"));
        return m;
    }

    private static Component comp(String name, ComponentType type) {
        Component c = new Component();
        c.id = ComponentId.of("" + name);
        c.name = name;
        c.type = type;
        c.technology = "test";
        return c;
    }

    private static Dependency dep(ComponentId from, ComponentId to) {
        Dependency d = new Dependency();
        d.fromId = from;
        d.toId = to;
        d.id = DependencyId.of(from, to);
        d.kind = "injection";
        d.derivedFrom = "annotation";
        d.confidence = 0.95;
        return d;
    }

    private static CallEdge callEdge(ComponentId fromComp, String fromMethod, ComponentId toComp, String toMethod) {
        CallEdge e = new CallEdge();
        e.fromComponentId = fromComp;
        e.fromMethod = fromMethod;
        e.toComponentId = toComp;
        e.toMethod = toMethod;
        e.callKind = "direct";
        return e;
    }

    private static Entrypoint ep(String id, String name, ComponentId compId, String method, String path) {
        Entrypoint e = new Entrypoint();
        e.id = EntrypointId.deserialize(id);
        e.name = name;
        e.componentId = compId;
        e.type = method != null ? EntrypointType.REST_ENDPOINT : EntrypointType.UNKNOWN;
        e.httpMethod = method;
        e.path = path;
        return e;
    }
}
