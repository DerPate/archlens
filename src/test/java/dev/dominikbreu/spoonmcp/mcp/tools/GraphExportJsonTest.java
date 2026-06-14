package dev.dominikbreu.spoonmcp.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.cache.ArchitectureGraph;
import dev.dominikbreu.spoonmcp.model.ComponentType;
import dev.dominikbreu.spoonmcp.model.MessagingBroker;
import dev.dominikbreu.spoonmcp.model.SourceInfo;
import dev.dominikbreu.spoonmcp.model.ids.AppId;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
import dev.dominikbreu.spoonmcp.model.ids.GraphNodeId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphExportJsonTest {

    @Test
    void keepsPipelineChainsAsProjectionsNotSnapshotNodes() throws Exception {
        ArchitectureGraph.GraphSnapshot snapshot = new ArchitectureGraph.GraphSnapshot(
                new ArchitectureGraph.GraphSnapshotMetadata(
                        9,
                        9,
                        9,
                        9,
                        false,
                        Map.of(
                                "Component",
                                1,
                                "Container",
                                1,
                                "PipelineChain",
                                1,
                                "RuntimeFlow",
                                1,
                                "RuntimeFlowStep",
                                1,
                                "Interface",
                                2,
                                "DataFlowPath",
                                2),
                        Map.of(
                                "CONTAINS",
                                1,
                                "HAS_SEGMENT",
                                2,
                                "HAS_STEP",
                                1,
                                "STARTED_BY",
                                1,
                                "VISITS",
                                1,
                                "EXPOSES",
                                2,
                                "REACHES",
                                1)),
                List.of(
                        new ArchitectureGraph.ComponentNode(
                                GraphNodeId.of("example.CustomerController"),
                                "CustomerController",
                                ComponentType.REST_RESOURCE,
                                "example.CustomerController",
                                "example",
                                AppId.of("app:example"),
                                "spring",
                                List.of("controller"),
                                new SourceInfo("CustomerController.java", 1, "annotation", 0.95),
                                0,
                                1,
                                1,
                                1,
                                1,
                                true,
                                true,
                                "entrypoint",
                                0,
                                0,
                                true,
                                "entrypoint",
                                null,
                                "boundary",
                                "type:REST_RESOURCE,package:example,name:CustomerController"),
                        new ArchitectureGraph.ContainerNode(
                                GraphNodeId.of("container:app:example:api"),
                                "api",
                                AppId.of("app:example"),
                                "spring",
                                "stereotype-convention"),
                        new ArchitectureGraph.InterfaceNode(
                                GraphNodeId.of("iface:example.CustomerController:rest_endpoint:PUT /customer/{id}"),
                                "PUT /customer/{id}",
                                "rest_endpoint",
                                "/customer/{id}",
                                ComponentId.of("example.CustomerController"),
                                AppId.of("app:example"),
                                "spring",
                                null,
                                null,
                                null,
                                null),
                        new ArchitectureGraph.InterfaceNode(
                                GraphNodeId.of("iface:example.CustomerEvents:messaging_producer:customers"),
                                "customers",
                                "messaging_producer",
                                null,
                                ComponentId.of("example.CustomerController"),
                                AppId.of("app:example"),
                                "kafka",
                                MessagingBroker.KAFKA,
                                "customers",
                                null,
                                null),
                        new ArchitectureGraph.RuntimeFlowNode(
                                GraphNodeId.of("flow:example.CustomerController#update"),
                                "flow:example.CustomerController#update",
                                EntrypointId.deserialize("example.CustomerController#update:PUT:/customer/{id}"),
                                1),
                        new ArchitectureGraph.RuntimeFlowStepNode(
                                GraphNodeId.of("flow:example.CustomerController#update:step:0"),
                                "CustomerController",
                                "flow:example.CustomerController#update",
                                0,
                                ComponentId.of("example.CustomerController"),
                                "REST_RESOURCE",
                                "PUT /customer/{id}"),
                        new ArchitectureGraph.PipelineChainNode(
                                GraphNodeId.of("chain:1"),
                                "chain:1",
                                2,
                                "example.CustomerController#update:PUT:/customer/{id}",
                                List.of("messaging")),
                        new ArchitectureGraph.DataFlowPathNode(
                                GraphNodeId.of("customer#customer"),
                                "customer#customer",
                                EntrypointId.deserialize("example.CustomerController#update:PUT:/customer/{id}"),
                                "customer",
                                1,
                                1),
                        new ArchitectureGraph.DataFlowPathNode(
                                GraphNodeId.of("address#event"),
                                "address#event",
                                EntrypointId.deserialize(
                                        "example.AddressMessageListener#listenCustomer:spring-listener:KAFKA:address"),
                                "event",
                                0,
                                0)),
                List.of(
                        edge(
                                "container:app:example:api",
                                "example.CustomerController",
                                "CONTAINS",
                                Map.of("source", "container.componentIds")),
                        edge(
                                "flow:example.CustomerController#update",
                                "example.CustomerController#update:PUT:/customer/{id}",
                                "STARTED_BY",
                                Map.of()),
                        edge(
                                "flow:example.CustomerController#update",
                                "flow:example.CustomerController#update:step:0",
                                "HAS_STEP",
                                Map.of("order", 0)),
                        edge(
                                "flow:example.CustomerController#update:step:0",
                                "example.CustomerController",
                                "VISITS",
                                Map.of("via", "PUT /customer/{id}")),
                        edge(
                                "iface:example.CustomerController:rest_endpoint:PUT /customer/{id}",
                                "example.CustomerController",
                                "EXPOSES",
                                Map.of("source", "interface.componentId")),
                        edge(
                                "iface:example.CustomerEvents:messaging_producer:customers",
                                "example.CustomerController",
                                "EXPOSES",
                                Map.of("source", "interface.componentId")),
                        edge("chain:1", "customer#customer", "HAS_SEGMENT", Map.of("segmentIndex", 0)),
                        edge(
                                "chain:1",
                                "address#event",
                                "HAS_SEGMENT",
                                Map.of(
                                        "segmentIndex",
                                        1,
                                        "incomingSinkId",
                                        "customer#customer:sink:0",
                                        "linkKind",
                                        "messaging")),
                        edge("customer#customer", "example.CustomerController", "REACHES", Map.of())));

        String json = GraphExportJson.write(snapshot, Instant.parse("2026-06-07T00:00:00Z"));

        assertThat(json)
                .contains("\"pipelines\"")
                .contains("\"id\" : \"chain:1\"")
                .contains("\"segmentIds\"")
                .contains("\"label\" : \"Component\"")
                .contains("\"label\" : \"Interface\"")
                .contains("\"interfaceType\" : \"messaging_producer\"")
                .contains("\"label\" : \"REACHES\"")
                .contains("\"label\" : \"EXPOSES\"")
                .doesNotContain("\"interfaceType\" : \"rest_endpoint\"")
                .doesNotContain("\"label\" : \"Container\"")
                .doesNotContain("\"label\" : \"CONTAINS\"")
                .doesNotContain("\"label\" : \"PipelineChain\"")
                .doesNotContain("\"label\" : \"HAS_SEGMENT\"")
                .doesNotContain("\"label\" : \"RuntimeFlow\"")
                .doesNotContain("\"label\" : \"RuntimeFlowStep\"")
                .doesNotContain("\"label\" : \"STARTED_BY\"")
                .doesNotContain("\"label\" : \"HAS_STEP\"")
                .doesNotContain("\"label\" : \"VISITS\"");
    }

    private static ArchitectureGraph.GraphEdge edge(
            String fromId, String toId, String label, Map<String, Object> properties) {
        return new ArchitectureGraph.GraphEdge(GraphNodeId.of(fromId), GraphNodeId.of(toId), label, properties);
    }
}
