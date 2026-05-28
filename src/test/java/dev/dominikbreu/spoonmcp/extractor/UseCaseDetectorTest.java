package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
import java.util.List;
import org.junit.jupiter.api.Test;

class UseCaseDetectorTest {

    private final UseCaseDetector detector = new UseCaseDetector();

    // ── deriveName ──────────────────────────────────────────────────────────────

    @Test
    void deriveNameForRestEndpoint() {
        Entrypoint ep = ep("createOrder", EntrypointType.REST_ENDPOINT);
        ep.httpMethod = "POST";
        assertThat(detector.deriveName(ep)).isEqualTo("POST Create Order");
    }

    @Test
    void deriveNameForRestEndpointNoHttpMethod() {
        Entrypoint ep = ep("listProducts", EntrypointType.REST_ENDPOINT);
        assertThat(detector.deriveName(ep)).isEqualTo("List Products");
    }

    @Test
    void deriveNameForMessagingConsumer() {
        Entrypoint ep = ep("onMessage", EntrypointType.MESSAGING_CONSUMER);
        ep.channelName = "order-events";
        assertThat(detector.deriveName(ep)).isEqualTo("Process Order Events");
    }

    @Test
    void deriveNameForMessagingConsumerNoChannel() {
        Entrypoint ep = ep("consumePayment", EntrypointType.MESSAGING_CONSUMER);
        assertThat(detector.deriveName(ep)).isEqualTo("Process Consume Payment");
    }

    @Test
    void deriveNameForMessagingProducer() {
        Entrypoint ep = ep("sendAlert", EntrypointType.MESSAGING_PRODUCER);
        ep.channelName = "alerts";
        assertThat(detector.deriveName(ep)).isEqualTo("Publish Alerts");
    }

    @Test
    void deriveNameForScheduler() {
        Entrypoint ep = ep("cleanupExpiredOrders", EntrypointType.SCHEDULER);
        assertThat(detector.deriveName(ep)).isEqualTo("Scheduled: Cleanup Expired Orders");
    }

    @Test
    void deriveNameForCdiEventObserver() {
        Entrypoint ep = ep("onOrderCreated", EntrypointType.CDI_EVENT_OBSERVER);
        ep.path = "com.example.events.OrderCreated";
        assertThat(detector.deriveName(ep)).isEqualTo("On Event: com.example.events.OrderCreated");
    }

    @Test
    void deriveNameFallsBackToName() {
        Entrypoint ep = ep("processInvoice", EntrypointType.JMS_CONSUMER);
        assertThat(detector.deriveName(ep)).isEqualTo("Consume Process Invoice");
    }

    // ── detect with injection-only fallback ─────────────────────────────────────

    @Test
    void detectCreatesOneUseCasePerEntrypoint() {
        ArchitectureModel model = buildSimpleModel();
        List<UseCase> useCases = detector.detect(model, UseCaseNamingConfig.empty());
        assertThat(useCases).hasSize(2);
    }

    @Test
    void detectPopulatesComponentsViaDepGraph() {
        ArchitectureModel model = buildSimpleModel();
        List<UseCase> useCases = detector.detect(model, UseCaseNamingConfig.empty());

        UseCase getOrder = useCases.stream()
                .filter(u -> u.entrypointId.equals(EntrypointId.deserialize("ep:getOrder")))
                .findFirst()
                .orElseThrow();

        assertThat(getOrder.componentIds)
                .contains(
                        ComponentId.of("comp:OrderResource"),
                        ComponentId.of("comp:OrderService"),
                        ComponentId.of("comp:OrderRepository"));
    }

    @Test
    void detectUsesConfiguredNameWhenPresent() {
        ArchitectureModel model = buildSimpleModel();
        UseCaseNamingConfig config = new UseCaseNamingConfig();
        config.names.put(model.entrypoints.get(0).id.serialize(), "Retrieve Order Details");
        List<UseCase> useCases = detector.detect(model, config);
        UseCase uc = useCases.stream()
                .filter(u -> u.entrypointId.equals(EntrypointId.deserialize("ep:getOrder")))
                .findFirst()
                .orElseThrow();
        assertThat(uc.name).isEqualTo("Retrieve Order Details");
    }

    @Test
    void detectFallsBackToDerivedNameWithoutConfig() {
        ArchitectureModel model = buildSimpleModel();
        List<UseCase> useCases = detector.detect(model, UseCaseNamingConfig.empty());
        UseCase uc = useCases.stream()
                .filter(u -> u.entrypointId.equals(EntrypointId.deserialize("ep:getOrder")))
                .findFirst()
                .orElseThrow();
        assertThat(uc.name).isEqualTo("GET Get Order");
    }

    // ── detect with call graph ───────────────────────────────────────────────────

    @Test
    void detectPopulatesMethodChainFromCallGraph() {
        ArchitectureModel model = buildSimpleModel();
        addCallEdge(model, "comp:OrderResource", "getOrder", "comp:OrderService", "find");
        addCallEdge(model, "comp:OrderService", "find", "comp:OrderRepository", "findById");

        List<UseCase> useCases = detector.detect(model, UseCaseNamingConfig.empty());
        UseCase uc = useCases.stream()
                .filter(u -> u.entrypointId.equals(EntrypointId.deserialize("ep:getOrder")))
                .findFirst()
                .orElseThrow();

        assertThat(uc.methodChain).isNotEmpty();
        assertThat(uc.methodChain)
                .anySatisfy(step ->
                        assertThat(step).contains("OrderResource.getOrder").contains("OrderService.find"));
        assertThat(uc.methodChain)
                .anySatisfy(
                        step -> assertThat(step).contains("OrderService.find").contains("OrderRepository.findById"));
    }

    @Test
    void detectDoesNotLoopOnCyclicCallEdges() {
        ArchitectureModel model = buildSimpleModel();
        addCallEdge(model, "comp:OrderResource", "getOrder", "comp:OrderService", "find");
        addCallEdge(model, "comp:OrderService", "find", "comp:OrderResource", "getOrder"); // cycle

        List<UseCase> useCases = detector.detect(model, UseCaseNamingConfig.empty());
        assertThat(useCases).isNotEmpty(); // must not throw / hang
    }

    @Test
    void detectSkipsLifecycleObserverUseCases() {
        ArchitectureModel model = new ArchitectureModel("test");
        Component handler = comp("LifecycleHandler", ComponentType.SERVICE);
        model.components.add(handler);

        Entrypoint shutdown = new Entrypoint();
        shutdown.id = EntrypointId.deserialize("ep:shutdown");
        shutdown.name = "onShutdown";
        shutdown.type = EntrypointType.CDI_EVENT_OBSERVER;
        shutdown.componentId = handler.id;
        model.entrypoints.add(shutdown);

        List<UseCase> useCases = detector.detect(model, UseCaseNamingConfig.empty());

        assertThat(useCases).isEmpty();
    }

    @Test
    void detectDoesNotInlineMessagingConsumerIntoProducerUseCase() {
        ArchitectureModel model = new ArchitectureModel("test");
        Component producer = comp("Producer", ComponentType.SERVICE);
        Component consumer = comp("Consumer", ComponentType.SERVICE);
        model.components.addAll(List.of(producer, consumer));

        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize("ep:Producer#send");
        ep.name = "send";
        ep.type = EntrypointType.SCHEDULER;
        ep.componentId = producer.id;
        model.entrypoints.add(ep);

        CallEdge edge = new CallEdge();
        edge.fromComponentId = producer.id;
        edge.fromMethod = "send";
        edge.toComponentId = consumer.id;
        edge.toMethod = "consume";
        edge.callKind = "messaging";
        model.callEdges.add(edge);

        List<UseCase> useCases = detector.detect(model, UseCaseNamingConfig.empty());
        UseCase useCase = useCases.getFirst();

        assertThat(useCase.componentIds).contains(producer.id);
        assertThat(useCase.componentIds).doesNotContain(consumer.id);
        assertThat(useCase.methodChain).noneMatch(step -> step.contains("Consumer.consume"));
    }

    @Test
    void detectTraversesThroughHiddenInlineComponents() {
        ArchitectureModel model = new ArchitectureModel("test");
        Component resource = comp("Resource", ComponentType.REST_RESOURCE);
        Component mapper = comp("Mapper", ComponentType.UTILITY);
        Component service = comp("Service", ComponentType.SERVICE);
        model.components.addAll(List.of(resource, mapper, service));

        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize("ep:Resource#get");
        ep.name = "get";
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.componentId = resource.id;
        model.entrypoints.add(ep);

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

        UseCase useCase = detector.detect(model, UseCaseNamingConfig.empty()).getFirst();

        assertThat(useCase.componentIds).contains(resource.id, service.id);
        assertThat(useCase.componentIds).doesNotContain(mapper.id);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static Entrypoint ep(String name, EntrypointType type) {
        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize("ep:" + name);
        ep.name = name;
        ep.type = type;
        ep.componentId = ComponentId.of("comp:SomeComponent");
        return ep;
    }

    private static ArchitectureModel buildSimpleModel() {
        ArchitectureModel model = new ArchitectureModel("test");

        Component resource = comp("OrderResource", ComponentType.REST_RESOURCE);
        Component service = comp("OrderService", ComponentType.SERVICE);
        Component repository = comp("OrderRepository", ComponentType.REPOSITORY);
        model.components.addAll(List.of(resource, service, repository));

        Entrypoint ep1 = new Entrypoint();
        ep1.id = EntrypointId.deserialize("ep:getOrder");
        ep1.name = "getOrder";
        ep1.type = EntrypointType.REST_ENDPOINT;
        ep1.httpMethod = "GET";
        ep1.componentId = ComponentId.of("comp:OrderResource");
        model.entrypoints.add(ep1);

        Entrypoint ep2 = new Entrypoint();
        ep2.id = EntrypointId.deserialize("ep:createOrder");
        ep2.name = "createOrder";
        ep2.type = EntrypointType.REST_ENDPOINT;
        ep2.httpMethod = "POST";
        ep2.componentId = ComponentId.of("comp:OrderResource");
        model.entrypoints.add(ep2);

        model.dependencies.add(dep("comp:OrderResource", "comp:OrderService", "injection"));
        model.dependencies.add(dep("comp:OrderService", "comp:OrderRepository", "injection"));

        return model;
    }

    private static Component comp(String name, ComponentType type) {
        Component c = new Component();
        c.id = ComponentId.of("comp:" + name);
        c.name = name;
        c.type = type;
        return c;
    }

    private static Dependency dep(String from, String to, String kind) {
        Dependency d = new Dependency();
        d.fromId = ComponentId.of(from);
        d.toId = ComponentId.of(to);
        d.kind = kind;
        return d;
    }

    private static void addCallEdge(
            ArchitectureModel model, String fromComp, String fromMethod, String toComp, String toMethod) {
        CallEdge e = new CallEdge();
        e.id = "call:" + fromComp + "#" + fromMethod + "->" + toComp + "#" + toMethod;
        e.fromComponentId = ComponentId.of(fromComp);
        e.fromMethod = fromMethod;
        e.toComponentId = ComponentId.of(toComp);
        e.toMethod = toMethod;
        e.callKind = "direct";
        model.callEdges.add(e);
    }
}
