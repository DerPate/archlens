package dev.dominikbreu.spoonmcp;

import dev.dominikbreu.spoonmcp.mcp.McpServer;
import dev.dominikbreu.spoonmcp.tracing.TracingConfig;
import io.opentelemetry.api.GlobalOpenTelemetry;

public class Main {
    private Main() {}

    public static void main(String[] args) {
        GlobalOpenTelemetry.set(TracingConfig.configure("spoon-mcp-server"));
        new McpServer().run();
    }
}
