package dev.dominikbreu.spoonmcp;

import dev.dominikbreu.spoonmcp.mcp.McpServer;

/**
 * Process entry point for the stdio Model Context Protocol server.
 */
public class Main {
    private Main() {}

    /**
     * Starts the MCP server on standard input and output.
     *
     * @param args command-line arguments, currently unused
     */
    public static void main(String[] args) {
        new McpServer().run();
    }
}
