package dev.dominikbreu.archlens.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class McpStructuredOutputWireTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void stableMode_emitsObjectSchemasAndStructuredErrors(@TempDir Path tempDir) throws Exception {
        try (ServerProcess server = ServerProcess.start(false, tempDir.resolve("stable-stderr.log"))) {
            JsonNode tool = server.tool("find_entrypoints");
            assertThat(tool.path("outputSchema").path("type").asString()).isEqualTo("object");
            assertThat(tool.path("outputSchema")
                            .path("properties")
                            .path("entrypoints")
                            .path("type")
                            .asString())
                    .isEqualTo("array");

            JsonNode result = server.callTool("find_entrypoints");
            assertThat(result.path("isError").asBoolean()).isTrue();
            assertThat(result.path("content").get(0).path("text").asString()).contains("No workspace indexed yet");
            assertThat(result.path("structuredContent").path("entrypoints").isArray())
                    .isTrue();
        }
    }

    @Test
    void stableMode_validatesIndexedGraphCollectionActions(@TempDir Path tempDir) throws Exception {
        try (ServerProcess server = ServerProcess.start(false, tempDir.resolve("stable-graph-stderr.log"))) {
            server.indexWorkspace();

            JsonNode nodes = server.callTool(
                    "query_architecture_graph", Map.of("action", "find_nodes", "label", "Component", "limit", 2));
            assertThat(nodes.path("isError").asBoolean()).isFalse();
            assertThat(nodes.path("structuredContent").path("action").asString())
                    .isEqualTo("find_nodes");
            assertThat(nodes.path("structuredContent").path("nodes").isArray()).isTrue();

            JsonNode edges = server.callTool("query_architecture_graph", Map.of("action", "find_edges", "limit", 2));
            assertThat(edges.path("isError").asBoolean()).isFalse();
            assertThat(edges.path("structuredContent").path("edges").isArray()).isTrue();

            JsonNode summary = server.callTool("query_architecture_graph", Map.of("action", "summary"));
            assertThat(summary.path("isError").asBoolean()).isFalse();
            assertThat(summary.path("structuredContent").path("action").asString())
                    .isEqualTo("summary");
        }
    }

    @Test
    void doesNotReadACacheFromTheWorkingDirectory(@TempDir Path tempDir) throws Exception {
        var model = new dev.dominikbreu.archlens.model.ArchitectureModel("checked-in");
        var app = new dev.dominikbreu.archlens.model.AppEntry();
        app.id = dev.dominikbreu.archlens.model.ids.AppId.of("checked-in");
        app.name = "checked-in";
        model.applications.add(app);
        new dev.dominikbreu.archlens.cache.ModelCache(
                        tempDir.resolve(".archlens-cache").toString())
                .store(model);

        try (ServerProcess server = ServerProcess.start(false, tempDir.resolve("cwd-stderr.log"))) {
            JsonNode apps = server.callTool("list_apps");
            assertThat(apps.path("isError").asBoolean()).as(apps.toString()).isTrue();
        }
    }

    @Test
    void pipelinedQuerySeesTheIndexSentBeforeIt(@TempDir Path tempDir) throws Exception {
        try (ServerProcess server = ServerProcess.start(false, tempDir.resolve("pipeline-stderr.log"))) {
            String fixture = Path.of("src/test/resources/testprojects/spring-pipeline-sample")
                    .toAbsolutePath()
                    .toString();
            server.send(101, "tools/call", toolCall("index_workspace", Map.of("paths", List.of(fixture))));
            server.send(102, "tools/call", toolCall("list_apps", Map.of()));

            JsonNode first = server.receive();
            JsonNode second = server.receive();

            assertThat(first.path("id").asInt()).isEqualTo(101);
            assertThat(second.path("id").asInt()).isEqualTo(102);
            assertThat(second.path("result").path("isError").asBoolean())
                    .as(second.toString())
                    .isFalse();
        }
    }

    @Test
    void pingIsAnsweredWhileAToolIsRunning(@TempDir Path tempDir) throws Exception {
        try (ServerProcess server = ServerProcess.start(false, tempDir.resolve("ping-stderr.log"))) {
            String fixture = Path.of("src/test/resources/testprojects/scheduler-hub")
                    .toAbsolutePath()
                    .toString();
            server.send(201, "tools/call", toolCall("index_workspace", Map.of("paths", List.of(fixture))));
            server.send(202, "ping", Map.of());

            assertThat(server.receive().path("id").asInt()).isEqualTo(202);
            assertThat(server.receive().path("id").asInt()).isEqualTo(201);
        }
    }

    private static Map<String, Object> toolCall(String name, Map<String, Object> arguments) {
        return Map.of("name", name, "arguments", arguments);
    }

    @Test
    void stableMode_listsByteIdenticalToolsAndPromptsInEveryJvm(@TempDir Path tempDir) throws Exception {
        assertListsIdenticalAcrossJvms(tempDir);
    }

    /** Map.of iteration order is salted per JVM, so only separate processes can expose it. */
    private static void assertListsIdenticalAcrossJvms(Path tempDir) throws Exception {
        Set<String> payloads = new HashSet<>();
        for (int run = 0; run < 4; run++) {
            try (ServerProcess server = ServerProcess.start(false, tempDir.resolve("run-" + run + ".log"))) {
                payloads.add(
                        server.request("tools/list", Map.of()).toString() + server.request("prompts/list", Map.of()));
            }
        }
        assertThat(payloads).as("distinct list payloads across JVMs").hasSize(1);
    }

    @Test
    void retiredDraftFlagDoesNotChangeTheOutputShape(@TempDir Path tempDir) throws Exception {
        try (ServerProcess server = ServerProcess.start(true, tempDir.resolve("flag-stderr.log"))) {
            for (JsonNode tool : server.request("tools/list", Map.of()).path("tools")) {
                assertThat(tool.path("outputSchema").path("type").asString())
                        .as(tool.path("name").asString())
                        .isEqualTo("object");
            }
            assertThat(server.callTool("find_entrypoints")
                            .path("structuredContent")
                            .path("entrypoints")
                            .isArray())
                    .isTrue();
        }
    }

    private static final class ServerProcess implements Closeable {
        private final Process process;
        private final BufferedWriter input;
        private final BufferedReader output;
        private int id;

        private ServerProcess(Process process) {
            this.process = process;
            input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        }

        static ServerProcess start(boolean retiredDraftFlag, Path errorLog) throws Exception {
            ProcessBuilder builder = new ProcessBuilder(
                            Path.of(System.getProperty("java.home"), "bin", "java")
                                    .toString(),
                            "-cp",
                            System.getProperty("java.class.path"),
                            "dev.dominikbreu.archlens.Main")
                    .directory(errorLog.getParent().toFile())
                    .redirectError(errorLog.toFile());
            if (retiredDraftFlag) {
                builder.environment().put("ARCHLENS_MCP_EXPERIMENTAL_DRAFT", "true");
            } else {
                builder.environment().remove("ARCHLENS_MCP_EXPERIMENTAL_DRAFT");
            }
            ServerProcess server = new ServerProcess(builder.start());
            server.request(
                    "initialize",
                    Map.of(
                            "protocolVersion", "2025-11-25",
                            "capabilities", Map.of(),
                            "clientInfo", Map.of("name", "wire-test", "version", "1")));
            server.notify("notifications/initialized", Map.of());
            return server;
        }

        JsonNode tool(String name) throws Exception {
            JsonNode tools = request("tools/list", Map.of()).path("tools");
            return StreamSupport.stream(tools.spliterator(), false)
                    .filter(tool -> name.equals(tool.path("name").asString()))
                    .findFirst()
                    .orElseThrow();
        }

        JsonNode callTool(String name) throws Exception {
            return callTool(name, Map.of());
        }

        JsonNode callTool(String name, Map<String, Object> arguments) throws Exception {
            return request("tools/call", Map.of("name", name, "arguments", arguments));
        }

        void indexWorkspace() throws Exception {
            Path fixture = Path.of("src/test/resources/testprojects/spring-pipeline-sample")
                    .toAbsolutePath()
                    .normalize();
            JsonNode result = callTool("index_workspace", Map.of("paths", java.util.List.of(fixture.toString())));
            assertThat(result.path("isError").asBoolean()).isFalse();
        }

        private JsonNode request(String method, Map<String, Object> params) throws Exception {
            id++;
            write(Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params));
            String line = output.readLine();
            if (line == null) throw new IOException("MCP server exited before responding");
            JsonNode response = JSON.readTree(line);
            assertThat(response.path("error").isMissingNode())
                    .as(response.toString())
                    .isTrue();
            return response.path("result");
        }

        void send(int requestId, String method, Map<String, Object> params) throws Exception {
            write(Map.of("jsonrpc", "2.0", "id", requestId, "method", method, "params", params));
        }

        JsonNode receive() throws Exception {
            String line = output.readLine();
            if (line == null) throw new IOException("MCP server exited before responding");
            return JSON.readTree(line);
        }

        private void notify(String method, Map<String, Object> params) throws Exception {
            write(Map.of("jsonrpc", "2.0", "method", method, "params", params));
        }

        private void write(Map<String, Object> message) throws Exception {
            input.write(JSON.writeValueAsString(message));
            input.newLine();
            input.flush();
        }

        @Override
        public void close() throws IOException {
            input.close();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            output.close();
        }
    }
}
