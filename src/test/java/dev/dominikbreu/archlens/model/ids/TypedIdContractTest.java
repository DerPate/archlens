package dev.dominikbreu.archlens.model.ids;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.Component;
import dev.dominikbreu.archlens.model.ComponentType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TypedIdContractTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void componentIdSerializesWithoutPrefixAndRoundTrips() {
        ComponentId id = ComponentId.of("com.acme.BillingService");
        assertThat(id.serialize()).isEqualTo("com.acme.BillingService");
        assertThat(ComponentId.deserialize("com.acme.BillingService")).isEqualTo(id);
    }

    @Test
    void entrypointIdSerializesWithoutPrefixAndRoundTrips() {
        EntrypointId id = EntrypointId.of(ComponentId.of("com.acme.OrderResource"), "create", "POST:/orders");
        assertThat(id.serialize()).isEqualTo("com.acme.OrderResource#create:POST:/orders");
        assertThat(EntrypointId.deserialize(id.serialize())).isEqualTo(id);
    }

    @Test
    void dependencyIdSerializesWithoutPrefixAndRoundTrips() {
        DependencyId id = DependencyId.of(ComponentId.of("com.acme.A"), ComponentId.of("com.acme.B"));
        assertThat(id.serialize()).isEqualTo("com.acme.A->com.acme.B");
        assertThat(DependencyId.deserialize("com.acme.A->com.acme.B")).isEqualTo(id);
    }

    @Test
    void useCaseIdSerializesFromEntrypointAndRoundTrips() {
        EntrypointId ep = EntrypointId.of(ComponentId.of("com.acme.OrderResource"), "create", "POST:/orders");
        UseCaseId id = UseCaseId.of(ep);
        assertThat(id.serialize()).isEqualTo("com.acme.OrderResource#create:POST:/orders");
        assertThat(id.serialize()).doesNotContain("usecase:");
        assertThat(UseCaseId.deserialize(id.serialize())).isEqualTo(id);
        assertThat(UseCaseId.deserialize(id.serialize()).entrypoint()).isEqualTo(ep);
    }

    @Test
    void dataFlowPathIdSerializesWithoutPrefixAndRoundTrips() {
        EntrypointId ep = EntrypointId.of(ComponentId.of("com.acme.OrderResource"), "create", "POST:/orders");
        DataFlowPathId id = DataFlowPathId.of(ep, "order");
        assertThat(id.serialize()).isEqualTo("com.acme.OrderResource#create:POST:/orders#order");
        assertThat(id.serialize()).doesNotContain("df:");
        DataFlowPathId round = DataFlowPathId.deserialize(id.serialize());
        assertThat(round).isEqualTo(id);
        assertThat(round.entrypoint()).isEqualTo(ep);
        assertThat(round.trackedParam()).isEqualTo("order");
    }

    @Test
    void fieldAccessIdWrapsCompositeValueAndRoundTrips() {
        FieldAccessId id = FieldAccessId.of("field:com.acme.A#m@cache:write");
        assertThat(id.serialize()).isEqualTo("field:com.acme.A#m@cache:write");
        assertThat(FieldAccessId.deserialize("field:com.acme.A#m@cache:write")).isEqualTo(id);
        assertThat(FieldAccessId.of(null)).isNull();
    }

    @Test
    void componentJsonEmitsBareIdAndReloads() {
        Component c = new Component();
        c.id = ComponentId.of("com.acme.BillingService");
        c.type = ComponentType.SERVICE;
        c.name = "BillingService";
        c.qualifiedName = "com.acme.BillingService";

        String json = mapper.writeValueAsString(c);
        assertThat(json).contains("\"id\":\"com.acme.BillingService\"");
        assertThat(json).doesNotContain("comp:");

        Component reloaded = mapper.readValue(json, Component.class);
        assertThat(reloaded.id).isEqualTo(c.id);
    }
}
