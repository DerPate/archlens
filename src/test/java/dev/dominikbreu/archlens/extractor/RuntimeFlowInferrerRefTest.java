package dev.dominikbreu.archlens.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.ArchitectureModel;
import dev.dominikbreu.archlens.model.Entrypoint;
import dev.dominikbreu.archlens.model.EntrypointType;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.EntrypointId;
import org.junit.jupiter.api.Test;

class RuntimeFlowInferrerRefTest {

    @Test
    void findEntrypointResolvesBySerializedId() {
        ArchitectureModel model = new ArchitectureModel();
        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.of(ComponentId.of("com.acme.OrderResource"), "create", "POST:/orders");
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.name = "create";
        model.entrypoints.add(ep);

        Entrypoint resolved = new RuntimeFlowInferrer().findEntrypoint(ep.id.serialize(), model);

        assertThat(resolved).isNotNull();
        assertThat(resolved.id).isEqualTo(ep.id);
    }
}
