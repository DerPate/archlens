package dev.dominikbreu.spoonmcp.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dominikbreu.spoonmcp.cache.ModelCache;
import dev.dominikbreu.spoonmcp.model.*;
import dev.dominikbreu.spoonmcp.model.ids.ComponentId;
import dev.dominikbreu.spoonmcp.model.ids.EntrypointId;
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
        ctrl.id = ComponentId.of("comp:CustomerController");
        ctrl.name = "CustomerController";
        ctrl.type = ComponentType.REST_RESOURCE;
        ctrl.technology = "spring";
        model.components.add(ctrl);

        model.entrypoints.addAll(List.of(
                restEp("ep:CustomerController#getAll:GET", "getAll", "GET", "/customer"),
                restEp("ep:CustomerController#get:GET", "get", "GET", "/customer/{id}"),
                restEp("ep:CustomerController#add:POST", "add", "POST", "/customer"),
                restEp("ep:CustomerController#update:PUT", "update", "PUT", "/customer/{id}"),
                restEp("ep:CustomerController#addAddr:POST", "addAddress", "POST", "/customer/{id}/address"),
                restEp("ep:CustomerController#getAddr:GET", "getAddress", "GET", "/customer/{id}/address/{aid}"),
                restEp("ep:AccountController#getAll:GET", "getAll", "GET", "/account"),
                restEp("ep:AccountController#add:POST", "add", "POST", "/account")));

        ModelCache cache = new ModelCache(null) {
            @Override
            public ArchitectureModel load() {
                return model;
            }
        };
        tool = new FindEntrypointsTool(cache);
    }

    // ── httpMethod filter ─────────────────────────────────────────────────────

    @Test
    void filterByHttpMethod_GET_returnsOnlyGetEndpoints() {
        String result = tool.execute(Map.of("httpMethod", "GET"));
        assertThat(result).contains("getAll [GET] /customer");
        assertThat(result).contains("get [GET] /customer/{id}");
        assertThat(result).contains("getAll [GET] /account");
        assertThat(result).doesNotContain("[POST]");
        assertThat(result).doesNotContain("[PUT]");
    }

    @Test
    void filterByHttpMethod_POST_returnsOnlyPostEndpoints() {
        String result = tool.execute(Map.of("httpMethod", "POST"));
        assertThat(result).contains("[POST] /customer");
        assertThat(result).contains("[POST] /customer/{id}/address");
        assertThat(result).contains("[POST] /account");
        assertThat(result).doesNotContain("[GET]");
        assertThat(result).doesNotContain("[PUT]");
    }

    @Test
    void filterByHttpMethod_isCaseInsensitive() {
        String lower = tool.execute(Map.of("httpMethod", "get"));
        String upper = tool.execute(Map.of("httpMethod", "GET"));
        assertThat(lower).isEqualTo(upper);
    }

    // ── path filter ───────────────────────────────────────────────────────────

    @Test
    void filterByPath_returnsAllEndpointsAtOrBelowPath() {
        String result = tool.execute(Map.of("path", "/customer"));
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
        String result = tool.execute(Map.of("path", "/customer/{id}"));
        assertThat(result).contains("/customer/{id}");
        assertThat(result).contains("/customer/{id}/address");
        assertThat(result).contains("/customer/{id}/address/{aid}");
        assertThat(result).doesNotContain("getAll [GET] /customer\n");
        assertThat(result).doesNotContain("add [POST] /customer\n");
    }

    @Test
    void filterByPath_exactPathMatchIncluded() {
        String result = tool.execute(Map.of("path", "/account"));
        assertThat(result).contains("/account");
        assertThat(result).doesNotContain("/customer");
    }

    // ── combined filters ──────────────────────────────────────────────────────

    @Test
    void combinedMethodAndPath_returnsOnlyMatchingMethodUnderPath() {
        String result = tool.execute(Map.of("httpMethod", "GET", "path", "/customer"));
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
        ep.componentId =
                ComponentId.of(id.startsWith("ep:Customer") ? "comp:CustomerController" : "comp:AccountController");
        return ep;
    }
}
