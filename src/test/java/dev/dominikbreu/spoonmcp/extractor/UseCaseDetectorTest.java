package dev.dominikbreu.spoonmcp.extractor;

import dev.dominikbreu.spoonmcp.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
            .filter(u -> u.entrypointId.equals("ep:getOrder"))
            .findFirst().orElseThrow();

        assertThat(getOrder.componentIds).contains(
            "comp:OrderResource", "comp:OrderService", "comp:OrderRepository");
    }

    @Test
    void detectUsesConfiguredNameWhenPresent() {
        ArchitectureModel model = buildSimpleModel();
        UseCaseNamingConfig config = new UseCaseNamingConfig();
        config.names.put("ep:getOrder", "Retrieve Order Details");
        List<UseCase> useCases = detector.detect(model, config);
        UseCase uc = useCases.stream().filter(u -> u.entrypointId.equals("ep:getOrder")).findFirst().orElseThrow();
        assertThat(uc.name).isEqualTo("Retrieve Order Details");
    }

    @Test
    void detectFallsBackToDerivedNameWithoutConfig() {
        ArchitectureModel model = buildSimpleModel();
        List<UseCase> useCases = detector.detect(model, UseCaseNamingConfig.empty());
        UseCase uc = useCases.stream().filter(u -> u.entrypointId.equals("ep:getOrder")).findFirst().orElseThrow();
        assertThat(uc.name).isEqualTo("GET Get Order");
    }

    // ── detect with call graph ───────────────────────────────────────────────────

    @Test
    void detectPopulatesMethodChainFromCallGraph() {
        ArchitectureModel model = buildSimpleModel();
        addCallEdge(model, "comp:OrderResource", "getOrder", "comp:OrderService", "find");
        addCallEdge(model, "comp:OrderService", "find", "comp:OrderRepository", "findById");

        List<UseCase> useCases = detector.detect(model, UseCaseNamingConfig.empty());
        UseCase uc = useCases.stream().filter(u -> u.entrypointId.equals("ep:getOrder")).findFirst().orElseThrow();

        assertThat(uc.methodChain).isNotEmpty();
        assertThat(uc.methodChain).anySatisfy(step ->
            assertThat(step).contains("OrderResource.getOrder").contains("OrderService.find"));
        assertThat(uc.methodChain).anySatisfy(step ->
            assertThat(step).contains("OrderService.find").contains("OrderRepository.findById"));
    }

    @Test
    void detectDoesNotLoopOnCyclicCallEdges() {
        ArchitectureModel model = buildSimpleModel();
        addCallEdge(model, "comp:OrderResource", "getOrder", "comp:OrderService", "find");
        addCallEdge(model, "comp:OrderService", "find", "comp:OrderResource", "getOrder"); // cycle

        List<UseCase> useCases = detector.detect(model, UseCaseNamingConfig.empty());
        assertThat(useCases).isNotEmpty(); // must not throw / hang
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static Entrypoint ep(String name, EntrypointType type) {
        Entrypoint ep = new Entrypoint();
        ep.id = "ep:" + name;
        ep.name = name;
        ep.type = type;
        ep.componentId = "comp:SomeComponent";
        return ep;
    }

    private static ArchitectureModel buildSimpleModel() {
        ArchitectureModel model = new ArchitectureModel("test");

        Component resource   = comp("OrderResource",   ComponentType.REST_RESOURCE);
        Component service    = comp("OrderService",    ComponentType.SERVICE);
        Component repository = comp("OrderRepository", ComponentType.REPOSITORY);
        model.components.addAll(List.of(resource, service, repository));

        Entrypoint ep1 = new Entrypoint();
        ep1.id = "ep:getOrder";
        ep1.name = "getOrder";
        ep1.type = EntrypointType.REST_ENDPOINT;
        ep1.httpMethod = "GET";
        ep1.componentId = "comp:OrderResource";
        model.entrypoints.add(ep1);

        Entrypoint ep2 = new Entrypoint();
        ep2.id = "ep:createOrder";
        ep2.name = "createOrder";
        ep2.type = EntrypointType.REST_ENDPOINT;
        ep2.httpMethod = "POST";
        ep2.componentId = "comp:OrderResource";
        model.entrypoints.add(ep2);

        model.dependencies.add(dep("comp:OrderResource", "comp:OrderService", "injection"));
        model.dependencies.add(dep("comp:OrderService", "comp:OrderRepository", "injection"));

        return model;
    }

    private static Component comp(String name, ComponentType type) {
        Component c = new Component();
        c.id = "comp:" + name;
        c.name = name;
        c.type = type;
        return c;
    }

    private static Dependency dep(String from, String to, String kind) {
        Dependency d = new Dependency();
        d.fromId = from;
        d.toId = to;
        d.kind = kind;
        return d;
    }

    private static void addCallEdge(ArchitectureModel model,
                                     String fromComp, String fromMethod,
                                     String toComp, String toMethod) {
        CallEdge e = new CallEdge();
        e.id = "call:" + fromComp + "#" + fromMethod + "->" + toComp + "#" + toMethod;
        e.fromComponentId = fromComp;
        e.fromMethod = fromMethod;
        e.toComponentId = toComp;
        e.toMethod = toMethod;
        e.callKind = "direct";
        model.callEdges.add(e);
    }
}
