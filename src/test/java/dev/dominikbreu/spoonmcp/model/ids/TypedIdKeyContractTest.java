package dev.dominikbreu.spoonmcp.model.ids;

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
        // Defensive of() means a legacy-prefixed lookup resolves to the same key.
        assertThat(byId.get(ComponentId.of("comp:com.acme.A"))).isEqualTo("A");
        assertThat(byId.get(ComponentId.deserialize("comp:com.acme.A"))).isEqualTo("A");
    }
}
