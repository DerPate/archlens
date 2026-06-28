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
import java.util.Map;
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
    void draftMode_emitsArraySchemasAndStructuredErrors(@TempDir Path tempDir) throws Exception {
        try (ServerProcess server = ServerProcess.start(true, tempDir.resolve("draft-stderr.log"))) {
            JsonNode tool = server.tool("find_entrypoints");
            assertThat(tool.path("outputSchema").path("type").asString()).isEqualTo("array");

            JsonNode result = server.callTool("find_entrypoints");
            assertThat(result.path("isError").asBoolean()).isTrue();
            assertThat(result.path("content").get(0).path("text").asString()).contains("No workspace indexed yet");
            assertThat(result.path("structuredContent").isArray()).isTrue();
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

        static ServerProcess start(boolean draft, Path errorLog) throws Exception {
            ProcessBuilder builder = new ProcessBuilder(
                            Path.of(System.getProperty("java.home"), "bin", "java")
                                    .toString(),
                            "-cp",
                            System.getProperty("java.class.path"),
                            "dev.dominikbreu.archlens.Main")
                    .redirectError(errorLog.toFile());
            if (draft) {
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
            return request("tools/call", Map.of("name", name, "arguments", Map.of()));
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
