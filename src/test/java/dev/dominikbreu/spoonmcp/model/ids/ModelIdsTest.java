package dev.dominikbreu.spoonmcp.model.ids;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ModelIdsTest {

    @Test
    void componentId_serializesAsQualifiedName() {
        var id = ComponentId.of("com.example.MyService");
        assertThat(id.qualifiedName()).isEqualTo("com.example.MyService");
        assertThat(id.serialize()).isEqualTo("com.example.MyService");
        assertThat(ComponentId.deserialize("com.example.MyService")).isEqualTo(id);
    }

    @Test
    void methodRef_roundtrips() {
        var comp = ComponentId.of("com.example.MyService");
        var ref = new MethodRef(comp, "process");
        assertThat(ref.component()).isEqualTo(comp);
        assertThat(ref.method()).isEqualTo("process");
    }

    @Test
    void entrypointId_structuredAccess() {
        var comp = ComponentId.of("com.example.Consumer");
        var ep = new EntrypointId(comp, "onMessage", "msg-in:orders");
        assertThat(ep.component()).isEqualTo(comp);
        assertThat(ep.method()).isEqualTo("onMessage");
        assertThat(ep.suffix()).isEqualTo("msg-in:orders");
        assertThat(ep.serialize()).isEqualTo("com.example.Consumer#onMessage:msg-in:orders");
    }

    @Test
    void fieldBinding_own_doesNotHaveOwner() {
        var binding = new FieldBinding.Own("store");
        assertThat(binding.fieldName()).isEqualTo("store");
    }

    @Test
    void fieldBinding_crossComponent_hasNonNullOwner() {
        var owner = ComponentId.of("com.example.DataService");
        var ref = new FieldRef(owner, "cache");
        var binding = new FieldBinding.CrossComponent(ref);
        assertThat(binding.ref().owner()).isEqualTo(owner);
        assertThat(binding.ref().fieldName()).isEqualTo("cache");
    }
}
