package dev.dominikbreu.spoonmcp.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ArchitectureModel;
import dev.dominikbreu.spoonmcp.model.Entrypoint;
import dev.dominikbreu.spoonmcp.model.EntrypointType;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
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
