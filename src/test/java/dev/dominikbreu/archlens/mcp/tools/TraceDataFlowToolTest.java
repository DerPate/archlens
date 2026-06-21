package dev.dominikbreu.archlens.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.model.*;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.DataFlowPathId;
import dev.dominikbreu.archlens.model.ids.EntrypointId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TraceDataFlowToolTest {

    @Test
    void pathFilterUsesPathPrefixNotSubstring() {
        // /customer should NOT match /budgetControl/customer/{id}
        ArchitectureModel model = new ArchitectureModel("test");

        Component ctrl = component("CustomerController", ComponentType.REST_RESOURCE);
        Component otherCtrl = component("BudgetControlController", ComponentType.REST_RESOURCE);
        model.components.addAll(List.of(ctrl, otherCtrl));

        Entrypoint customerEp = ep("CustomerController#get", "get", "/customer/{id}", "GET", ctrl.id);
        Entrypoint budgetEp = ep(
                "BudgetControlController#get",
                "get",
                "/budgetControl/customer/{customerId}/missionPlan/{missionPlanId}",
                "GET",
                otherCtrl.id);
        model.entrypoints.addAll(List.of(customerEp, budgetEp));

        // add a call edge so callEdges is non-empty (tool requires this)
        CallEdge edge = new CallEdge();
        edge.id = "call:ctrl#get->ctrl#find";
        edge.fromComponentId = ctrl.id;
        edge.fromMethod = "get";
        edge.toComponentId = ctrl.id;
        edge.toMethod = "find";
        edge.callKind = "direct";
        model.callEdges.add(edge);

        DataFlowPath customerPath = dataFlowPath("ep:CustomerController#get:GET#id", customerEp.id, "id");
        DataFlowPath budgetPath =
                dataFlowPath("ep:BudgetControlController#get:GET#customerId", budgetEp.id, "customerId");
        model.dataFlowPaths.addAll(List.of(customerPath, budgetPath));

        ModelCache cache = new ModelCache(null);
        cache.indexInMemory(model);
        TraceDataFlowTool tool = new TraceDataFlowTool(cache);

        String result = tool.execute(Map.of("entrypointName", "/customer"));

        assertThat(result).contains("GET /customer/{id}");
        assertThat(result).doesNotContain("budgetControl");
    }

    // ── characterization: filters + format branches ───────────────────────────

    @Test
    void emptyCallEdges_reportsNoCallGraph() {
        ArchitectureModel model = new ArchitectureModel("test");
        String result = tool(model).execute(Map.of());
        assertThat(result).contains("No call-graph data available");
    }

    @Test
    void noModel_reportsNoWorkspace() {
        ModelCache empty = new ModelCache(null);
        empty.indexInMemory(null);
        assertThat(new TraceDataFlowTool(empty).execute(Map.of())).contains("No workspace indexed");
    }

    @Test
    void entrypointIdFilter_matchesBySerializedId() {
        ArchitectureModel model = richModel();
        String result = tool(model).execute(Map.of("entrypointId", "CustomerController#get"));
        assertThat(result).contains("GET /customer/{id}");
    }

    @Test
    void paramFilter_selectsMatchingParam() {
        ArchitectureModel model = richModel();
        String result = tool(model).execute(Map.of("param", "id"));
        assertThat(result).contains("param: id");
    }

    @Test
    void sinkKindFilter_selectsPathsWithMatchingSink() {
        ArchitectureModel model = richModel();
        String result = tool(model).execute(Map.of("sinkKind", "store"));
        assertThat(result).contains("[store]");
    }

    @Test
    void filtersExcludingAll_reportNoPaths() {
        ArchitectureModel model = richModel();
        String result = tool(model).execute(Map.of("param", "doesNotExist"));
        assertThat(result).isEqualTo("No data-flow paths found for the given filters.");
    }

    @Test
    void format_rendersStepsSinksStoreFieldAndSourceLocation() {
        ArchitectureModel model = richModel();
        String result = tool(model).execute(Map.of("entrypointId", "CustomerController#get"));

        assertThat(result)
                .contains("1 data-flow path(s):")
                .contains("→ param: id")
                .contains("id: ep:CustomerController#get:GET#id")
                .doesNotContain("DataFlowPathId[")
                .contains("flow:")
                .contains("1. CustomerController.get (as 'id')")
                .contains("sinks:")
                .contains("[store] StateStore.put")
                .contains("field=cache")
                .contains("(StateStore.java:42)");
    }

    @Test
    void format_prefersTopologyWhenPresent() {
        ArchitectureModel model = richModel();
        DataFlowPath path = model.dataFlowPaths.getFirst();
        path.flowNodes.add(new DataFlowNode(
                "n0", DataFlowNode.Kind.ROOT, path.entrypointId.component(), "CustomerController", "get", "id", null));
        path.flowNodes.add(new DataFlowNode(
                "n1",
                DataFlowNode.Kind.METHOD,
                ComponentId.of("Validator"),
                "Validator",
                "accept",
                "id",
                new SourceInfo("Validator.java", 12, "invocation", 0.9)));
        path.flowNodes.add(new DataFlowNode(
                "n2",
                DataFlowNode.Kind.METHOD,
                ComponentId.of("Validator"),
                "Validator",
                "reject",
                "id",
                new SourceInfo("Validator.java", 14, "invocation", 0.9)));
        path.flowEdges.add(new DataFlowEdge("n0", "n1", DataFlowEdge.Kind.CONDITIONAL, "b0", "b0:then", "then"));
        path.flowEdges.add(new DataFlowEdge("n0", "n2", DataFlowEdge.Kind.CONDITIONAL, "b0", "b0:else", "else"));
        DataFlowBranch branch = new DataFlowBranch(
                "b0", DataFlowBranch.Kind.IF, new SourceInfo("Validator.java", 11, "if", 1.0), List.of());
        branch.arms.add(new DataFlowBranchArm("b0:then", "b0", "then", "n1"));
        branch.arms.add(new DataFlowBranchArm("b0:else", "b0", "else", "n2"));
        path.branches.add(branch);

        String result = tool(model).execute(Map.of("entrypointId", "CustomerController#get"));

        assertThat(result)
                .contains("flow graph:")
                .contains("N0 CustomerController.get [root]")
                .contains("branches:")
                .contains("B0 IF Validator.java:11")
                .contains("then -> N1")
                .contains("else -> N2")
                .contains("edges:")
                .contains("N0 -> N1 [then] (B0/b0:then)")
                .contains("N0 -> N2 [else] (B0/b0:else)")
                .doesNotContain("  flow:\n");
    }

    @Test
    void format_entrypointMissing_fallsBackToSerializedId() {
        ArchitectureModel model = richModel();
        // path referencing an entrypoint id not present in model.entrypoints
        DataFlowPath orphan = dataFlowPath("ep:Ghost#run#x", EntrypointId.deserialize("Ghost#run"), "x");
        model.dataFlowPaths.add(orphan);
        String result = tool(model).execute(Map.of("param", "x"));
        assertThat(result).contains("Ghost#run");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static TraceDataFlowTool tool(ArchitectureModel model) {
        ModelCache cache = new ModelCache(null);
        cache.indexInMemory(model);
        return new TraceDataFlowTool(cache);
    }

    private static ArchitectureModel richModel() {
        ArchitectureModel model = new ArchitectureModel("test");
        Component ctrl = component("CustomerController", ComponentType.REST_RESOURCE);
        Component store = component("StateStore", ComponentType.SERVICE);
        model.components.addAll(List.of(ctrl, store));

        Entrypoint ep = ep("CustomerController#get", "get", "/customer/{id}", "GET", ctrl.id);
        model.entrypoints.add(ep);

        CallEdge edge = new CallEdge();
        edge.id = "call:ctrl#get->store#put";
        edge.fromComponentId = ctrl.id;
        edge.fromMethod = "get";
        edge.toComponentId = store.id;
        edge.toMethod = "put";
        edge.callKind = "direct";
        model.callEdges.add(edge);

        DataFlowPath path = dataFlowPath("ep:CustomerController#get:GET#id", ep.id, "id");
        path.steps.add(new DataFlowStep(0, ctrl.id, "CustomerController", "get", "id"));
        DataFlowSink sink = new DataFlowSink(
                DataFlowSink.Kind.STORE,
                store.id,
                "StateStore",
                "put",
                new SourceInfo("path/StateStore.java", 42, "field-write", 0.9));
        sink.fieldName = "cache";
        path.sinks.add(sink);
        model.dataFlowPaths.add(path);
        return model;
    }

    private static Component component(String name, ComponentType type) {
        Component c = new Component();
        c.id = ComponentId.of("" + name);
        c.name = name;
        c.type = type;
        c.stereotypes = new java.util.ArrayList<>();
        return c;
    }

    private static Entrypoint ep(String id, String name, String path, String httpMethod, ComponentId compId) {
        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize(id);
        ep.name = name;
        ep.path = path;
        ep.httpMethod = httpMethod;
        ep.componentId = compId;
        ep.type = EntrypointType.REST_ENDPOINT;
        return ep;
    }

    private static DataFlowPath dataFlowPath(String id, EntrypointId entrypointId, String param) {
        DataFlowPath p = new DataFlowPath();
        p.id = DataFlowPathId.deserialize(id);
        p.entrypointId = entrypointId;
        p.trackedParam = param;
        p.steps = new java.util.ArrayList<>();
        p.sinks = new java.util.ArrayList<>();
        return p;
    }
}
