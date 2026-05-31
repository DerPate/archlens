package dev.dominikbreu.spoonmcp.likec4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.model.AppEntry;
import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.Dependency;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import dev.dominikbreu.spoonmcp.model.ids.AppId;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.DependencyId;
import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class LikeC4WorkspaceProjectorTest {

    @Test
    void projectsWorkspaceDocumentWithSystemComponentsAndStandardViews() throws Exception {
        ModelCache cache = indexFixtureProject("state-handoff");
        ArchitectureModel model = cache.load();
        AppEntry app = model.applications.getFirst();

        LikeC4Document document = new LikeC4WorkspaceProjector().projectWorkspace(cache.graph(), model, app, 12);

        assertTrue(
                document.elementKinds().contains("system"),
                document.elementKinds().toString());
        assertTrue(
                document.elementKinds().contains("component"),
                document.elementKinds().toString());

        LikeC4Element system = document.elements().stream()
                .filter(element -> "system".equals(element.kind()))
                .findFirst()
                .orElseThrow();
        assertEquals(app.id.serialize(), system.id());
        assertEquals(app.name, system.title());
        assertEquals(app.id.serialize(), system.sourceId());

        List<LikeC4Element> components = document.elements().stream()
                .filter(element -> "component".equals(element.kind()))
                .toList();
        assertFalse(components.isEmpty(), "expected projected component elements");
        assertTrue(
                components.stream().allMatch(element -> element.id().startsWith("")),
                "expected graph component ids as document ids: " + components);

        Set<String> viewIds = document.views().stream().map(LikeC4View::id).collect(Collectors.toSet());
        assertEquals(Set.of("context", "container", "component"), viewIds);

        LikeC4View context = view(document, "context");
        LikeC4View container = view(document, "container");
        LikeC4View component = view(document, "component");

        assertEquals(List.of(app.id.serialize()), context.includes());
        assertTrue(
                container.includes().contains(app.id.serialize()),
                container.includes().toString());
        assertTrue(
                component
                        .includes()
                        .containsAll(container.includes().stream()
                                .filter(id -> !app.id.serialize().equals(id))
                                .toList()),
                component.includes().toString());
        assertEquals(
                components.stream().map(LikeC4Element::id).collect(Collectors.toSet()),
                Set.copyOf(component.includes()));
    }

    @Test
    void architectureSelectionPrefersPrimaryComponentsOverEntityNoise() {
        ArchitectureModel model = new ArchitectureModel("layered-fixture");
        AppEntry app = new AppEntry();
        app.id = AppId.of("app:layered");
        app.name = "layered";

        Component controller = component("CustomerController", ComponentType.REST_RESOURCE);
        Component service = component("CustomerService", ComponentType.SERVICE);
        Component repository = component("CustomerRepository", ComponentType.REPOSITORY);
        Component client = component("BillingClient", ComponentType.HTTP_CLIENT);
        Component customer = component("Customer", ComponentType.ENTITY);
        Component invoice = component("Invoice", ComponentType.ENTITY);
        Component address = component("Address", ComponentType.ENTITY);
        Component audit = component("AuditEntry", ComponentType.ENTITY);

        app.componentIds.addAll(List.of(
                controller.id, service.id, repository.id, client.id, customer.id, invoice.id, address.id, audit.id));
        model.applications.add(app);
        model.components.addAll(List.of(controller, service, repository, client, customer, invoice, address, audit));
        model.dependencies.addAll(List.of(
                dependency(controller.id, service.id, "injection"),
                dependency(service.id, repository.id, "injection"),
                dependency(service.id, client.id, "http-client"),
                dependency(repository.id, customer.id, "jpa"),
                dependency(customer.id, invoice.id, "field-reference"),
                dependency(customer.id, address.id, "field-reference"),
                dependency(invoice.id, audit.id, "field-reference"),
                dependency(address.id, audit.id, "field-reference")));

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        LikeC4Document document = new LikeC4WorkspaceProjector().projectWorkspace(graph, model, app, 5);

        List<String> componentTitles = document.elements().stream()
                .filter(element -> "component".equals(element.kind()))
                .map(LikeC4Element::title)
                .toList();
        assertEquals(
                List.of("CustomerController", "CustomerService", "CustomerRepository", "BillingClient", "Customer"),
                componentTitles);

        LikeC4View container = view(document, "container");
        LikeC4View component = view(document, "component");
        assertFalse(
                container.includes().contains(customer.id.serialize()),
                "container should not show supporting entity nodes");
        assertTrue(
                component.includes().contains(customer.id.serialize()),
                "component view should retain a connected supporting entity");
        assertFalse(component.notes().isEmpty(), "component view should explain why supporting entities appear");
    }

    @Test
    void projectsRestAndMessagingEntrypointsAndTheirOwningComponents() {
        ArchitectureModel model = new ArchitectureModel("entrypoint-fixture");
        AppEntry app = new AppEntry();
        app.id = AppId.of("app:entrypoints");
        app.name = "entrypoints";

        Component account = component("AccountController", ComponentType.REST_RESOURCE);
        Component listener = component("AddressMessageListener", ComponentType.SCHEDULER);
        Component service = component("AccountService", ComponentType.SERVICE);
        Component entity = component("Account", ComponentType.ENTITY);

        app.componentIds.addAll(List.of(account.id, listener.id, service.id, entity.id));
        model.applications.add(app);
        model.components.addAll(List.of(account, listener, service, entity));

        Entrypoint addAccount = entrypoint("addAccount", EntrypointType.REST_ENDPOINT, "POST", "/account", account.id);
        Entrypoint listenAddress =
                entrypoint("listenAddress", EntrypointType.MESSAGING_CONSUMER, "KAFKA", "address", listener.id);
        model.entrypoints.addAll(List.of(addAccount, listenAddress));

        model.dependencies.addAll(List.of(
                dependency(account.id, service.id, "injection"),
                dependency(listener.id, service.id, "injection"),
                dependency(service.id, entity.id, "jpa")));

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        LikeC4Document document = new LikeC4WorkspaceProjector().projectWorkspace(graph, model, app, 6);

        assertTrue(
                document.elementKinds().contains("entrypoint"),
                document.elementKinds().toString());
        Set<String> elementIds =
                document.elements().stream().map(LikeC4Element::id).collect(Collectors.toSet());
        assertTrue(elementIds.contains(addAccount.id.serialize()), elementIds.toString());
        assertTrue(elementIds.contains(listenAddress.id.serialize()), elementIds.toString());
        assertTrue(elementIds.contains(account.id.serialize()), elementIds.toString());
        assertTrue(elementIds.contains(listener.id.serialize()), elementIds.toString());
        assertTrue(
                document.relationships().stream()
                        .anyMatch(relationship -> addAccount.id.serialize().equals(relationship.sourceId())
                                && account.id.serialize().equals(relationship.targetId())),
                document.relationships().toString());
        assertTrue(
                document.relationships().stream()
                        .anyMatch(relationship -> listenAddress.id.serialize().equals(relationship.sourceId())
                                && listener.id.serialize().equals(relationship.targetId())),
                document.relationships().toString());
    }

    @Test
    void projectionDoesNotLoseEntrypointsPastGraphQueryCaps() {
        ArchitectureModel model = new ArchitectureModel("large-entrypoint-fixture");
        AppEntry app = new AppEntry();
        app.id = AppId.of("app:large");
        app.name = "large";

        for (int i = 0; i < 120; i++) {
            Component component = component("Utility%03d".formatted(i), ComponentType.UTILITY);
            app.componentIds.add(component.id);
            model.components.add(component);
        }

        Component listener = component("ZzzAddressMessageListener", ComponentType.SCHEDULER);
        app.componentIds.add(listener.id);
        model.components.add(listener);
        model.applications.add(app);

        Entrypoint listenAddress =
                entrypoint("listenAddress", EntrypointType.MESSAGING_CONSUMER, "KAFKA", "address", listener.id);
        model.entrypoints.add(listenAddress);

        ArchitectureGraph graph = new ArchitectureGraph();
        graph.rebuild(model);

        LikeC4Document document = new LikeC4WorkspaceProjector().projectWorkspace(graph, model, app, 6);

        Set<String> elementIds =
                document.elements().stream().map(LikeC4Element::id).collect(Collectors.toSet());
        assertTrue(elementIds.contains(listenAddress.id.serialize()), elementIds.toString());
        assertTrue(elementIds.contains(listener.id.serialize()), elementIds.toString());
    }

    @Test
    void relationshipEndpointsReferToDocumentElementsWhenRelationshipsExist() throws Exception {
        ModelCache cache = indexFixtureProject("state-handoff");
        ArchitectureModel model = cache.load();
        AppEntry app = model.applications.getFirst();

        LikeC4Document document = new LikeC4WorkspaceProjector().projectWorkspace(cache.graph(), model, app, 12);

        assertFalse(document.relationships().isEmpty(), "state-handoff fixture should project relationships");
        Set<String> elementIds =
                document.elements().stream().map(LikeC4Element::id).collect(Collectors.toSet());

        for (LikeC4Relationship relationship : document.relationships()) {
            assertTrue(elementIds.contains(relationship.sourceId()), "missing source " + relationship);
            assertTrue(elementIds.contains(relationship.targetId()), "missing target " + relationship);
        }
    }

    private static LikeC4View view(LikeC4Document document, String id) {
        LikeC4View view = document.views().stream()
                .filter(candidate -> id.equals(candidate.id()))
                .findFirst()
                .orElse(null);
        assertNotNull(view, "missing view " + id);
        return view;
    }

    private static ModelCache indexFixtureProject(String name) throws Exception {
        Class<?> fixtures = Class.forName("dev.dominikbreu.spoonmcp.mcp.tools.ToolTestFixtures");
        Method method = fixtures.getDeclaredMethod("indexFixtureProject", String.class);
        method.setAccessible(true);
        return (ModelCache) method.invoke(null, name);
    }

    private static Component component(String name, ComponentType type) {
        Component component = new Component();
        component.name = name;
        component.qualifiedName = "com.example." + name;
        component.id = ComponentId.of(component.qualifiedName);
        component.type = type;
        return component;
    }

    private static Dependency dependency(ComponentId fromId, ComponentId toId, String kind) {
        Dependency dependency = new Dependency();
        dependency.id = DependencyId.of(fromId, toId);
        dependency.fromId = fromId;
        dependency.toId = toId;
        dependency.kind = kind;
        dependency.confidence = 0.9;
        return dependency;
    }

    private static Entrypoint entrypoint(
            String name, EntrypointType type, String methodOrBroker, String pathOrChannel, ComponentId componentId) {
        Entrypoint entrypoint = new Entrypoint();
        entrypoint.id = EntrypointId.of(componentId, name, methodOrBroker + ":" + pathOrChannel);
        entrypoint.name = name;
        entrypoint.type = type;
        entrypoint.componentId = componentId;
        if (type == EntrypointType.REST_ENDPOINT) {
            entrypoint.httpMethod = methodOrBroker;
            entrypoint.path = pathOrChannel;
        } else if (type == EntrypointType.MESSAGING_CONSUMER) {
            entrypoint.channelName = pathOrChannel;
        }
        return entrypoint;
    }
}
