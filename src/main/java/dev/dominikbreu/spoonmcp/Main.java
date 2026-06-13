package dev.dominikbreu.spoonmcp;

import dev.dominikbreu.spoonmcp.mcp.McpServer;
import dev.dominikbreu.spoonmcp.tracing.TracingConfig;
import io.opentelemetry.api.GlobalOpenTelemetry;

/** Application entry point — configures tracing and starts the MCP server. */
public class Main {
    private Main() {}

    /**
     * Starts the MCP server with OpenTelemetry tracing configured.
     *
     * @param args command-line arguments (currently unused)
     */
    public static void main(String[] args) {
        GlobalOpenTelemetry.set(TracingConfig.configure("spoon-mcp-server"));
        new McpServer().run();
    }
}
