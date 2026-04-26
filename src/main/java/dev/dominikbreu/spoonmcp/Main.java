package dev.dominikbreu.spoonmcp;

import dev.dominikbreu.spoonmcp.mcp.McpServer;

/**
 * Process entry point for the stdio Model Context Protocol server.
 */
public class Main {
    private Main() {}

    /**
     * Starts the MCP JSON-RPC loop on standard input and output.
     *
     * @param args command-line arguments, currently unused
     * @throws Exception if the server loop cannot be started
     */
    public static void main(String[] args) throws Exception {
        new McpServer().run();
    }
}
