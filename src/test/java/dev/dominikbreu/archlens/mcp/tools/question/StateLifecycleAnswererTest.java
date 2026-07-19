package dev.dominikbreu.archlens.mcp.tools.question;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.GraphQuery;
import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.ComponentType;
import dev.dominikbreu.archlens.model.FieldAccess;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.FieldAccessId;
import dev.dominikbreu.archlens.model.ids.FieldBinding;
import dev.dominikbreu.archlens.model.ids.FieldRef;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StateLifecycleAnswererTest {

    @Test
    void reportsWritersReadersAndHandoffsForAKnownField() {
        ArchitectureModel model = new ArchitectureModel("test");
        model.components.add(component("OrderService", ComponentType.SERVICE));
        model.components.add(component("StatePublisher", ComponentType.SCHEDULER));

        FieldAccess write = fieldAccess(FieldAccess.Kind.WRITE, "OrderService", "consume", "OrderService", "snapshots");
        FieldAccess read = fieldAccess(FieldAccess.Kind.READ, "StatePublisher", "tick", "OrderService", "snapshots");
        model.fieldAccesses.add(write);
        model.fieldAccesses.add(read);

        GraphQuery graph = GraphQuery.from(model);

        Answer result = StateLifecycleAnswerer.answer(graph, Map.of("field", "snapshots"), new QueryPlanRecorder());
        Map<String, Object> structured = result.structured("state_lifecycle", null, null);
        Map<String, Object> answer = EndpointContextAnswererTest.answer(structured);

        assertThat(EndpointContextAnswererTest.list(answer, "writers")).isNotEmpty();
        assertThat(EndpointContextAnswererTest.list(answer, "readers")).isNotEmpty();
        assertThat(EndpointContextAnswererTest.list(answer, "handoffs")).isNotEmpty();
    }

    private static Component component(String name, ComponentType type) {
        Component component = new Component();
        component.id = ComponentId.of(name);
        component.name = name;
        component.qualifiedName = "com.example." + name;
        component.type = type;
        return component;
    }

    private static FieldAccess fieldAccess(
            FieldAccess.Kind kind, String componentId, String method, String ownerComponentId, String fieldName) {
        FieldAccess access = new FieldAccess();
        access.kind = kind;
        access.componentId = ComponentId.of(componentId);
        access.method = method;
        if (componentId.equals(ownerComponentId)) {
            access.fieldBinding = new FieldBinding.Own(fieldName);
        } else {
            access.fieldBinding =
                    new FieldBinding.CrossComponent(new FieldRef(ComponentId.of(ownerComponentId), fieldName));
        }
        access.id = FieldAccessId.of("field:" + componentId + "#" + method + "@" + fieldName + ":"
                + kind.name().toLowerCase());
        return access;
    }
}
