package dev.dominikbreu.spoonmcp.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
import dev.dominikbreu.spoonmcp.model.ids.GraphNodeId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphDataProjectionTest {

    @Test
    void projectsPipelineChainsIntoViewerReadySummaries() {
        ArchitectureGraph.GraphSnapshot snapshot = new ArchitectureGraph.GraphSnapshot(
                new ArchitectureGraph.GraphSnapshotMetadata(
                        5,
                        4,
                        5,
                        4,
                        false,
                        Map.of("PipelineChain", 1, "DataFlowPath", 2, "DataFlowSink", 1, "Component", 1),
                        Map.of("HAS_SEGMENT", 2, "REACHES", 1, "AT_COMPONENT", 1)),
                List.of(
                        new ArchitectureGraph.PipelineChainNode(
                                GraphNodeId.of("chain:12"),
                                "chain:12",
                                2,
                                "de.homeinstead.phoenix.controller.ServiceRequestController#update:PUT:/serviceRequest/{id}",
                                List.of("messaging")),
                        new ArchitectureGraph.DataFlowPathNode(
                                GraphNodeId.of("df:serviceRequest#serviceRequest"),
                                "df:serviceRequest#serviceRequest",
                                EntrypointId.deserialize(
                                        "de.homeinstead.phoenix.controller.ServiceRequestController#update:PUT:/serviceRequest/{id}"),
                                "serviceRequest",
                                1,
                                1),
                        new ArchitectureGraph.DataFlowPathNode(
                                GraphNodeId.of("df:address#event"),
                                "df:address#event",
                                EntrypointId.deserialize(
                                        "de.homeinstead.phoenix.inbound.AddressMessageListener#listenCustomer:spring-listener:KAFKA:address"),
                                "event",
                                1,
                                1),
                        node(
                                "sink:serviceRequest:3",
                                "DataFlowSink",
                                "AddressMessage",
                                Map.of("componentId", "de.homeinstead.phoenix.inbound.AddressMessageListener")),
                        node(
                                "de.homeinstead.phoenix.inbound.AddressMessageListener",
                                "Component",
                                "AddressMessageListener",
                                Map.of())),
                List.of(
                        edge("chain:12", "df:serviceRequest#serviceRequest", "HAS_SEGMENT", Map.of("segmentIndex", 0)),
                        edge(
                                "chain:12",
                                "df:address#event",
                                "HAS_SEGMENT",
                                Map.of(
                                        "segmentIndex",
                                        1,
                                        "linkKind",
                                        "messaging",
                                        "viaChannel",
                                        "address",
                                        "incomingSinkId",
                                        "sink:serviceRequest:3")),
                        edge("df:serviceRequest#serviceRequest", "sink:serviceRequest:3", "REACHES", Map.of()),
                        edge(
                                "sink:serviceRequest:3",
                                "de.homeinstead.phoenix.inbound.AddressMessageListener",
                                "AT_COMPONENT",
                                Map.of())));

        GraphDataProjection.ViewerProjections projections = GraphDataProjection.from(snapshot);

        assertThat(projections.pipelines()).singleElement().satisfies(pipeline -> {
            assertThat(pipeline.id()).isEqualTo("chain:12");
            assertThat(pipeline.title()).isEqualTo("ServiceRequestController.update PUT /serviceRequest/{id}");
            assertThat(pipeline.subtitle()).isEqualTo("messaging, 2 segments");
            assertThat(pipeline.segmentIds()).containsExactly("df:serviceRequest#serviceRequest", "df:address#event");
            assertThat(pipeline.nodeIds())
                    .containsExactly(
                            "df:serviceRequest#serviceRequest",
                            "df:address#event",
                            "sink:serviceRequest:3",
                            "de.homeinstead.phoenix.inbound.AddressMessageListener");
            assertThat(pipeline.edgeKeys())
                    .containsExactly(
                            "df:serviceRequest#serviceRequest->sink:serviceRequest:3:REACHES:2",
                            "sink:serviceRequest:3->de.homeinstead.phoenix.inbound.AddressMessageListener:AT_COMPONENT:3");
            assertThat(pipeline.segments())
                    .extracting(GraphDataProjection.PipelineSegmentProjection::title)
                    .containsExactly(
                            "ServiceRequestController.update PUT /serviceRequest/{id} #serviceRequest",
                            "AddressMessageListener.listenCustomer KAFKA address #event");
            assertThat(pipeline.segments())
                    .extracting(GraphDataProjection.PipelineSegmentProjection::startNodeId)
                    .containsExactly("df:serviceRequest#serviceRequest", "df:address#event");
            assertThat(pipeline.segments().getFirst().endNodeIds()).containsExactly("sink:serviceRequest:3");
            assertThat(pipeline.segments().getFirst().nodeIds())
                    .containsExactly(
                            "df:serviceRequest#serviceRequest",
                            "sink:serviceRequest:3",
                            "de.homeinstead.phoenix.inbound.AddressMessageListener");
            assertThat(pipeline.segments().getFirst().edgeKeys())
                    .containsExactly(
                            "df:serviceRequest#serviceRequest->sink:serviceRequest:3:REACHES:2",
                            "sink:serviceRequest:3->de.homeinstead.phoenix.inbound.AddressMessageListener:AT_COMPONENT:3");
            assertThat(pipeline.segments().get(1).endNodeIds()).isEmpty();
            assertThat(pipeline.segments().get(1).nodeIds()).containsExactly("df:address#event");
            assertThat(pipeline.segments().get(1).edgeKeys()).isEmpty();
        });
    }

    @Test
    void doesNotExpandSelectedPipelineThroughSharedSinkComponents() {
        ArchitectureGraph.GraphSnapshot snapshot = new ArchitectureGraph.GraphSnapshot(
                new ArchitectureGraph.GraphSnapshotMetadata(
                        7,
                        8,
                        7,
                        8,
                        false,
                        Map.of("PipelineChain", 1, "DataFlowPath", 3, "DataFlowSink", 2, "Component", 1),
                        Map.of("HAS_SEGMENT", 2, "REACHES", 2, "AT_COMPONENT", 2, "LINKS_TO", 1, "WORKFLOW_LINK", 1)),
                List.of(
                        new ArchitectureGraph.PipelineChainNode(
                                GraphNodeId.of("chain:selected"),
                                "chain:selected",
                                2,
                                "example.CustomerController#update:PUT:/customer/{id}",
                                List.of("messaging")),
                        path(
                                "df:customer#customer",
                                "example.CustomerController#update:PUT:/customer/{id}",
                                "customer"),
                        path(
                                "df:address#event",
                                "example.AddressMessageListener#listenCustomer:spring-listener:KAFKA:address",
                                "event"),
                        path("df:other#customer", "example.OtherController#update:PUT:/other/{id}", "customer"),
                        node("sink:customer:0", "DataFlowSink", "KafkaJsonProducer", Map.of()),
                        node("sink:other:0", "DataFlowSink", "KafkaJsonProducer", Map.of()),
                        node("example.KafkaJsonProducer", "Component", "KafkaJsonProducer", Map.of())),
                List.of(
                        edge("chain:selected", "df:customer#customer", "HAS_SEGMENT", Map.of("segmentIndex", 0)),
                        edge(
                                "chain:selected",
                                "df:address#event",
                                "HAS_SEGMENT",
                                Map.of(
                                        "segmentIndex",
                                        1,
                                        "incomingSinkId",
                                        "sink:customer:0",
                                        "linkKind",
                                        "messaging",
                                        "viaChannel",
                                        "address")),
                        edge("df:customer#customer", "sink:customer:0", "REACHES", Map.of()),
                        edge("sink:customer:0", "example.KafkaJsonProducer", "AT_COMPONENT", Map.of()),
                        edge("sink:customer:0", "df:address#event", "LINKS_TO", Map.of("linkKind", "messaging")),
                        edge("df:other#customer", "sink:other:0", "REACHES", Map.of()),
                        edge("sink:other:0", "example.KafkaJsonProducer", "AT_COMPONENT", Map.of()),
                        edge("df:other#customer", "df:address#event", "WORKFLOW_LINK", Map.of())));

        GraphDataProjection.PipelineProjection pipeline =
                GraphDataProjection.from(snapshot).pipelines().getFirst();

        assertThat(pipeline.nodeIds())
                .containsExactly(
                        "df:customer#customer", "df:address#event", "sink:customer:0", "example.KafkaJsonProducer");
        assertThat(pipeline.edgeKeys())
                .containsExactly(
                        "df:customer#customer->sink:customer:0:REACHES:2",
                        "sink:customer:0->df:address#event:LINKS_TO:4",
                        "sink:customer:0->example.KafkaJsonProducer:AT_COMPONENT:3");
    }

    private static ArchitectureGraph.GraphNode node(
            String id, String label, String name, Map<String, Object> properties) {
        return new ArchitectureGraph.UnknownNode(GraphNodeId.of(id), label, name, properties);
    }

    private static ArchitectureGraph.DataFlowPathNode path(String id, String entrypointId, String trackedParam) {
        return new ArchitectureGraph.DataFlowPathNode(
                GraphNodeId.of(id), id, EntrypointId.deserialize(entrypointId), trackedParam, 1, 1);
    }

    private static ArchitectureGraph.GraphEdge edge(
            String fromId, String toId, String label, Map<String, Object> properties) {
        return new ArchitectureGraph.GraphEdge(GraphNodeId.of(fromId), GraphNodeId.of(toId), label, properties);
    }
}
