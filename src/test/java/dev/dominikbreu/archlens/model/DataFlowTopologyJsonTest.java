package dev.dominikbreu.archlens.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.model.ids.ComponentId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DataFlowTopologyJsonTest {

    @Test
    void dataFlowPathInitializesTopologyLists() {
        DataFlowPath path = new DataFlowPath();

        assertThat(path.flowNodes).isEmpty();
        assertThat(path.flowEdges).isEmpty();
        assertThat(path.branches).isEmpty();
    }

    @Test
    void topologyRoundTripsThroughJson() throws Exception {
        DataFlowPath path = new DataFlowPath();
        SourceInfo source = new SourceInfo("src/main/java/example/OrderController.java", 42, "data-flow", 0.9);
        DataFlowNode root = new DataFlowNode(
                "node-root",
                DataFlowNode.Kind.ROOT,
                ComponentId.of("example.OrderController"),
                "OrderController",
                "create",
                "order",
                source);
        DataFlowNode sink = new DataFlowNode(
                "node-sink",
                DataFlowNode.Kind.SINK,
                ComponentId.of("example.OrderRepository"),
                "OrderRepository",
                "save",
                "entity",
                source);
        DataFlowBranchArm thenArm = new DataFlowBranchArm("then", "branch-if", "valid", "node-sink");
        DataFlowBranch branch =
                new DataFlowBranch("branch-if", DataFlowBranch.Kind.IF, source, java.util.List.of(thenArm));
        DataFlowEdge edge =
                new DataFlowEdge("node-root", "node-sink", DataFlowEdge.Kind.CONDITIONAL, "branch-if", "then", "valid");

        path.flowNodes.add(root);
        path.flowNodes.add(sink);
        path.flowEdges.add(edge);
        path.branches.add(branch);

        JsonMapper mapper = new JsonMapper();
        DataFlowPath restored = mapper.readValue(mapper.writeValueAsString(path), DataFlowPath.class);

        assertThat(restored.flowNodes).hasSize(2);
        assertThat(restored.flowNodes.get(0).id).isEqualTo("node-root");
        assertThat(restored.flowNodes.get(0).kind).isEqualTo(DataFlowNode.Kind.ROOT);
        assertThat(restored.flowNodes.get(0).componentId).isEqualTo(ComponentId.of("example.OrderController"));
        assertThat(restored.flowEdges).hasSize(1);
        assertThat(restored.flowEdges.get(0).kind).isEqualTo(DataFlowEdge.Kind.CONDITIONAL);
        assertThat(restored.flowEdges.get(0).branchId).isEqualTo("branch-if");
        assertThat(restored.flowEdges.get(0).branchArmId).isEqualTo("then");
        assertThat(restored.branches).hasSize(1);
        assertThat(restored.branches.get(0).kind).isEqualTo(DataFlowBranch.Kind.IF);
        assertThat(restored.branches.get(0).arms).hasSize(1);
        assertThat(restored.branches.get(0).arms.get(0).entryNodeId).isEqualTo("node-sink");
    }
}
