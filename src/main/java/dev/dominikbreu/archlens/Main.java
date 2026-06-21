package dev.dominikbreu.archlens;

import dev.dominikbreu.archlens.dashboard.Dashboard;
import dev.dominikbreu.archlens.mcp.McpServer;
import dev.dominikbreu.archlens.tracing.TracingConfig;
import io.opentelemetry.api.GlobalOpenTelemetry;
import java.io.IOException;

/**
 * Application entry point — configures tracing, then starts the MCP server, or, when launched
 * directly in an interactive terminal (no MCP client piping JSON-RPC), the standalone dashboard.
 */
public class Main {
    private Main() {}

    /**
     * @param args command-line arguments (currently unused)
     * @throws IOException if the standalone dashboard's terminal cannot be opened
     */
    public static void main(String[] args) throws IOException {
        GlobalOpenTelemetry.set(TracingConfig.configure("archlens"));
        McpServer server = new McpServer();
        if (System.console() != null) {
            new Dashboard(server.buildToolSpecifications(), McpServer.SERVER_VERSION).run();
        } else {
            server.run();
        }
    }
}
