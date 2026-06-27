package dev.dominikbreu.archlens.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.archlens.cache.ModelCache;
import dev.dominikbreu.archlens.model.*;
import dev.dominikbreu.archlens.model.ids.AppId;
import dev.dominikbreu.archlens.model.ids.ComponentId;
import dev.dominikbreu.archlens.model.ids.EntrypointId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FindEntrypointsToolTest {

    private FindEntrypointsTool tool;
    private ArchitectureModel model;

    @BeforeEach
    void setUp() {
        model = new ArchitectureModel("test");
        Component ctrl = new Component();
        ctrl.id = ComponentId.of("CustomerController");
        ctrl.name = "CustomerController";
        ctrl.type = ComponentType.REST_RESOURCE;
        ctrl.technology = "spring";
        model.components.add(ctrl);

        model.entrypoints.addAll(List.of(
                restEp("CustomerController#getAll:GET", "getAll", "GET", "/customer"),
                restEp("CustomerController#get:GET", "get", "GET", "/customer/{id}"),
                restEp("CustomerController#add:POST", "add", "POST", "/customer"),
                restEp("CustomerController#update:PUT", "update", "PUT", "/customer/{id}"),
                restEp("CustomerController#addAddr:POST", "addAddress", "POST", "/customer/{id}/address"),
                restEp("CustomerController#getAddr:GET", "getAddress", "GET", "/customer/{id}/address/{aid}"),
                restEp("AccountController#getAll:GET", "getAll", "GET", "/account"),
                restEp("AccountController#add:POST", "add", "POST", "/account")));

        ModelCache cache = new ModelCache(null);
        cache.indexInMemory(model);
        tool = new FindEntrypointsTool(cache);
    }

    // ── httpMethod filter ─────────────────────────────────────────────────────

    @Test
    void filterByHttpMethod_GET_returnsOnlyGetEndpoints() {
        String result = tool.execute(Map.of("httpMethod", "GET")).text();
        assertThat(result).contains("getAll [GET] /customer");
        assertThat(result).contains("get [GET] /customer/{id}");
        assertThat(result).contains("getAll [GET] /account");
        assertThat(result).contains("Component: CustomerController");
        assertThat(result).doesNotContain("ComponentId[");
        assertThat(result).doesNotContain("[POST]");
        assertThat(result).doesNotContain("[PUT]");
    }

    @Test
    void filterByHttpMethod_POST_returnsOnlyPostEndpoints() {
        String result = tool.execute(Map.of("httpMethod", "POST")).text();
        assertThat(result).contains("[POST] /customer");
        assertThat(result).contains("[POST] /customer/{id}/address");
        assertThat(result).contains("[POST] /account");
        assertThat(result).doesNotContain("[GET]");
        assertThat(result).doesNotContain("[PUT]");
    }

    @Test
    void filterByHttpMethod_isCaseInsensitive() {
        String lower = tool.execute(Map.of("httpMethod", "get")).text();
        String upper = tool.execute(Map.of("httpMethod", "GET")).text();
        assertThat(lower).isEqualTo(upper);
    }

    // ── path filter ───────────────────────────────────────────────────────────

    @Test
    void filterByPath_returnsAllEndpointsAtOrBelowPath() {
        String result = tool.execute(Map.of("path", "/customer")).text();
        assertThat(result).contains("/customer");
        assertThat(result).contains("/customer/{id}");
        assertThat(result).contains("/customer/{id}/address");
        assertThat(result).contains("/customer/{id}/address/{aid}");
        assertThat(result).doesNotContain("/account");
    }

    @Test
    void filterByPath_withParameterisedPrefix_returnsSubPaths() {
        // /customer/{id} should match /customer/{id}, /customer/{id}/address, /customer/{id}/address/{aid}
        // but NOT /customer (the bare list endpoint)
        String result = tool.execute(Map.of("path", "/customer/{id}")).text();
        assertThat(result).contains("/customer/{id}");
        assertThat(result).contains("/customer/{id}/address");
        assertThat(result).contains("/customer/{id}/address/{aid}");
        assertThat(result).doesNotContain("getAll [GET] /customer\n");
        assertThat(result).doesNotContain("add [POST] /customer\n");
    }

    @Test
    void filterByPath_exactPathMatchIncluded() {
        String result = tool.execute(Map.of("path", "/account")).text();
        assertThat(result).contains("/account");
        assertThat(result).doesNotContain("/customer");
    }

    // ── appId filter ──────────────────────────────────────────────────────────

    @Test
    void filterByAppId_exactId_returnsOnlyEntrypointsOwnedByThatApp() {
        String result =
                twoAppTool().execute(Map.of("appId", "app:customer-service")).text();
        assertThat(result).contains("/customer");
        assertThat(result).doesNotContain("/account");
    }

    @Test
    void filterByAppId_partialIdWithoutPrefix_stillMatches() {
        // "app:" prefix and exact id shouldn't be required — same partial-match
        // convention as the rest of graph search (query_architecture_graph find_nodes).
        String result =
                twoAppTool().execute(Map.of("appId", "customer-service")).text();
        assertThat(result).contains("/customer");
        assertThat(result).doesNotContain("/account");
    }

    private static FindEntrypointsTool twoAppTool() {
        ArchitectureModel twoAppModel = new ArchitectureModel("test");

        Component customerCtrl = new Component();
        customerCtrl.id = ComponentId.of("CustomerController");
        customerCtrl.name = "CustomerController";
        customerCtrl.type = ComponentType.REST_RESOURCE;
        customerCtrl.technology = "spring";

        Component accountCtrl = new Component();
        accountCtrl.id = ComponentId.of("AccountController");
        accountCtrl.name = "AccountController";
        accountCtrl.type = ComponentType.REST_RESOURCE;
        accountCtrl.technology = "spring";

        twoAppModel.components.addAll(List.of(customerCtrl, accountCtrl));

        AppEntry customerApp = new AppEntry();
        customerApp.id = AppId.of("app:customer-service");
        customerApp.name = "customer-service";
        customerApp.componentIds.add(customerCtrl.id);

        AppEntry accountApp = new AppEntry();
        accountApp.id = AppId.of("app:account-service");
        accountApp.name = "account-service";
        accountApp.componentIds.add(accountCtrl.id);

        twoAppModel.applications.addAll(List.of(customerApp, accountApp));

        twoAppModel.entrypoints.addAll(List.of(
                restEp("CustomerController#getAll:GET", "getAll", "GET", "/customer"),
                restEp("AccountController#getAll:GET", "getAll", "GET", "/account")));

        ModelCache twoAppCache = new ModelCache(null);
        twoAppCache.indexInMemory(twoAppModel);
        return new FindEntrypointsTool(twoAppCache);
    }

    // ── combined filters ──────────────────────────────────────────────────────

    @Test
    void combinedMethodAndPath_returnsOnlyMatchingMethodUnderPath() {
        String result =
                tool.execute(Map.of("httpMethod", "GET", "path", "/customer")).text();
        assertThat(result).contains("getAll [GET] /customer");
        assertThat(result).contains("get [GET] /customer/{id}");
        assertThat(result).contains("getAddress [GET] /customer/{id}/address/{aid}");
        assertThat(result).doesNotContain("[POST]");
        assertThat(result).doesNotContain("[PUT]");
        assertThat(result).doesNotContain("/account");
    }

    // ── pathPrefixMatchesForDiscovery helper ──────────────────────────────────

    @Test
    void discoveryMatch_barePathMatchesAllDescendants() {
        assertThat(FindEntrypointsTool.pathPrefixMatchesForDiscovery("/customer/{id}", "/customer"))
                .isTrue();
        assertThat(FindEntrypointsTool.pathPrefixMatchesForDiscovery("/customer/{id}/address", "/customer"))
                .isTrue();
        assertThat(FindEntrypointsTool.pathPrefixMatchesForDiscovery("/customer", "/customer"))
                .isTrue();
    }

    @Test
    void discoveryMatch_parametrisedFilterMatchesSubPaths() {
        // Unlike pathPrefixMatches used in single-lookup, discovery allows {id} in filter
        assertThat(FindEntrypointsTool.pathPrefixMatchesForDiscovery("/customer/{id}/address/{aid}", "/customer/{id}"))
                .isTrue();
        assertThat(FindEntrypointsTool.pathPrefixMatchesForDiscovery("/customer/{id}/address", "/customer/{id}"))
                .isTrue();
    }

    @Test
    void discoveryMatch_doesNotMatchSiblingPaths() {
        assertThat(FindEntrypointsTool.pathPrefixMatchesForDiscovery("/account", "/customer"))
                .isFalse();
        assertThat(FindEntrypointsTool.pathPrefixMatchesForDiscovery("/customerContact", "/customer"))
                .isFalse();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Entrypoint restEp(String id, String name, String method, String path) {
        Entrypoint ep = new Entrypoint();
        ep.id = EntrypointId.deserialize(id);
        ep.name = name;
        ep.type = EntrypointType.REST_ENDPOINT;
        ep.httpMethod = method;
        ep.path = path;
        ep.componentId = ComponentId.of(id.startsWith("Customer") ? "CustomerController" : "AccountController");
        return ep;
    }
}
