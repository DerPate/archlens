package dev.dominikbreu.spoonmcp.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.model.*;
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

        Entrypoint customerEp = ep("ep:CustomerController#get", "get", "/customer/{id}", "GET", ctrl.id);
        Entrypoint budgetEp = ep(
                "ep:BudgetControlController#get",
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

        DataFlowPath customerPath = dataFlowPath("df:ep:CustomerController#get:GET#id", customerEp.id, "id");
        DataFlowPath budgetPath =
                dataFlowPath("df:ep:BudgetControlController#get:GET#customerId", budgetEp.id, "customerId");
        model.dataFlowPaths.addAll(List.of(customerPath, budgetPath));

        ModelCache cache = new ModelCache(null, ModelCache.CacheBackend.JSON) {
            @Override
            public ArchitectureModel load() {
                return model;
            }
        };
        TraceDataFlowTool tool = new TraceDataFlowTool(cache);

        String result = tool.execute(Map.of("entrypointName", "/customer"));

        assertThat(result).contains("GET /customer/{id}");
        assertThat(result).doesNotContain("budgetControl");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Component component(String name, ComponentType type) {
        Component c = new Component();
        c.id = "comp:" + name;
        c.name = name;
        c.type = type;
        c.stereotypes = new java.util.ArrayList<>();
        return c;
    }

    private static Entrypoint ep(String id, String name, String path, String httpMethod, String compId) {
        Entrypoint ep = new Entrypoint();
        ep.id = id;
        ep.name = name;
        ep.path = path;
        ep.httpMethod = httpMethod;
        ep.componentId = compId;
        ep.type = EntrypointType.REST_ENDPOINT;
        return ep;
    }

    private static DataFlowPath dataFlowPath(String id, String entrypointId, String param) {
        DataFlowPath p = new DataFlowPath();
        p.id = id;
        p.entrypointId = entrypointId;
        p.trackedParam = param;
        p.steps = new java.util.ArrayList<>();
        p.sinks = new java.util.ArrayList<>();
        return p;
    }
}
