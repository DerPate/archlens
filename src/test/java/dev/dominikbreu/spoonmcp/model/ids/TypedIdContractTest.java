package dev.dominikbreu.spoonmcp.model.ids;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.Component;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TypedIdContractTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void componentIdSerializesWithoutPrefixAndRoundTrips() {
        ComponentId id = ComponentId.of("com.acme.BillingService");
        assertThat(id.serialize()).isEqualTo("com.acme.BillingService");
        assertThat(ComponentId.deserialize("comp:com.acme.BillingService")).isEqualTo(id);
        assertThat(ComponentId.deserialize("com.acme.BillingService")).isEqualTo(id);
    }

    @Test
    void entrypointIdSerializesWithoutPrefixAndRoundTrips() {
        EntrypointId id = EntrypointId.of(ComponentId.of("com.acme.OrderResource"), "create", "POST:/orders");
        assertThat(id.serialize()).isEqualTo("com.acme.OrderResource#create:POST:/orders");
        assertThat(EntrypointId.deserialize(id.serialize())).isEqualTo(id);
        assertThat(EntrypointId.deserialize("ep:" + id.serialize())).isEqualTo(id);
    }

    @Test
    void dependencyIdSerializesWithoutPrefixAndRoundTrips() {
        DependencyId id = DependencyId.of(ComponentId.of("com.acme.A"), ComponentId.of("com.acme.B"));
        assertThat(id.serialize()).isEqualTo("com.acme.A->com.acme.B");
        assertThat(DependencyId.deserialize("dep:com.acme.A->com.acme.B")).isEqualTo(id);
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

    @Test
    void legacyPrefixedComponentJsonStillDeserializes() {
        Component reloaded = mapper.readValue("{\"id\":\"comp:com.acme.BillingService\"}", Component.class);
        assertThat(reloaded.id).isEqualTo(ComponentId.of("com.acme.BillingService"));
    }
}
