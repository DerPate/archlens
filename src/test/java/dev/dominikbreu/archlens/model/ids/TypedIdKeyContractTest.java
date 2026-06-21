package dev.dominikbreu.archlens.model.ids;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TypedIdKeyContractTest {

    @Test
    void typedIdsAreUsableAsValueEqualMapKeys() {
        Map<ComponentId, String> byId = new HashMap<>();
        byId.put(ComponentId.of("com.acme.A"), "A");
        assertThat(byId.get(ComponentId.of("com.acme.A"))).isEqualTo("A");
        assertThat(byId.get(ComponentId.deserialize("com.acme.A"))).isEqualTo("A");
    }
}
