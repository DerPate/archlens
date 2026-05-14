# Install & Usage

This guide walks through building Spoon MCP Server and wiring it into an MCP client.

## Prerequisites

- Java 21 or newer (`java -version`)
- Maven 3.9 or newer (`mvn -v`)
- An MCP-capable client (Claude Desktop, Claude Code, or any client that can launch a stdio MCP server)

### Installing prerequisites on Linux

Pick the snippet for your distribution. All three install OpenJDK 21 and Maven from the official package repositories.

**Debian / Ubuntu**

```sh
sudo apt update
sudo apt install -y openjdk-21-jdk maven git
```

If `openjdk-21-jdk` is not yet in your release, enable backports or use the Adoptium repository:

```sh
sudo apt install -y wget apt-transport-https gpg
wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo gpg --dearmor -o /usr/share/keyrings/adoptium.gpg
echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update
sudo apt install -y temurin-21-jdk maven git
```

**Fedora / RHEL / Rocky / Alma**

```sh
sudo dnf install -y java-21-openjdk-devel maven git
```

**Arch / Manjaro**

```sh
sudo pacman -S --needed jdk21-openjdk maven git
sudo archlinux-java set java-21-openjdk
```

**openSUSE**

```sh
sudo zypper install -y java-21-openjdk-devel maven git
```

**SDKMAN! (any distro)**

If you want multiple JDKs side by side without root:

```sh
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.5-tem
sdk install maven
```

Verify:

```sh
java -version   # openjdk version "21" or higher
mvn -v          # Apache Maven 3.9 or higher
```

If `java -version` reports an older JDK, switch the default:

- Debian/Ubuntu: `sudo update-alternatives --config java`
- Fedora/RHEL: `sudo alternatives --config java`
- Arch: `sudo archlinux-java set java-21-openjdk`
- SDKMAN!: `sdk default java 21.0.5-tem`

## 1. Build

Clone the repo and build the shaded server jar:

```sh
git clone https://github.com/DerPate/spoon-mcp-server.git
cd spoon-mcp-server
mvn clean package
```

The runnable, dependency-shaded jar is produced at:

```
target/spoon-mcp-server.jar
```

Note the absolute path — every MCP client config below needs it.

## 2. Smoke test

Verify the server starts and speaks JSON-RPC over stdio:

```sh
java -jar target/spoon-mcp-server.jar < examples/jsonrpc/initialize.json
```

You should see a JSON response with `serverInfo` and the supported `protocolVersion`.

The example files in `examples/jsonrpc/` are JSONL-style request streams: one complete JSON-RPC object per line. This matches the SDK stdio transport. Do not pretty-print a single JSON-RPC envelope across multiple physical lines when piping directly to stdin.

For multi-message flows such as `tools-list.json`, `prompts-list.json`, or `prompt-get-analyze-workspace.json`, use an MCP client or a small driver that sends each JSONL message sequentially and waits for responses. A plain shell redirection can close stdin before the SDK has finished processing later messages.

## 3. Configure your MCP client

The server is a stdio MCP server. Replace `/abs/path/to` with the absolute path on your machine. MCP clients handle the JSONL framing and initialization handshake for you.

### Claude Desktop

Edit `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS) or `%APPDATA%\Claude\claude_desktop_config.json` (Windows):

```json
{
  "mcpServers": {
    "spoon": {
      "command": "java",
      "args": [
        "-jar",
        "/abs/path/to/spoon-mcp-server/target/spoon-mcp-server.jar"
      ]
    }
  }
}
```

Restart Claude Desktop. The Spoon tools should appear under the tools menu.

### Claude Code

Add the server with the CLI:

```sh
claude mcp add spoon -- java -jar /abs/path/to/spoon-mcp-server/target/spoon-mcp-server.jar
```

Or edit `~/.claude.json` (or your project `.mcp.json`) directly:

```json
{
  "mcpServers": {
    "spoon": {
      "command": "java",
      "args": [
        "-jar",
        "/abs/path/to/spoon-mcp-server/target/spoon-mcp-server.jar"
      ]
    }
  }
}
```

### Generic MCP client

Any client that speaks MCP over stdio works. Configure it to launch:

```
java -jar /abs/path/to/spoon-mcp-server/target/spoon-mcp-server.jar
```

with no arguments. The server reads JSON-RPC from stdin and writes responses to stdout.

## 4. First run

Once the client is connected, ask the agent (or call directly) to index a workspace:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "index_workspace",
    "arguments": { "paths": ["/abs/path/to/your/java/project"] }
  }
}
```

After indexing, every other tool (`list_apps`, `find_components`, `render_mermaid_flowchart`, …) operates on the cached model. See [TOOLS.md](TOOLS.md) for the full tool catalog.

## Configuration

| Setting | Env var | JVM property | Default | Effect |
|---|---|---|---|---|
| Cache backend | `SPOON_MCP_CACHE_BACKEND` | `spoonmcp.cache.backend` | `json` | Set to `graph` to eagerly maintain the graph projection on cache load/store. |

Example with the graph backend:

```sh
SPOON_MCP_CACHE_BACKEND=graph java -jar target/spoon-mcp-server.jar
```

The cache itself is written to `.spoon-mcp-cache/` in the working directory.

## Troubleshooting

- **`UnsupportedClassVersionError`** — the JVM running the jar is older than 21. Check `java -version` and point the client at a Java 21+ binary if needed (e.g. `"command": "/path/to/jdk-21/bin/java"`).
- **Client shows no tools** — confirm the absolute jar path resolves and the smoke test from step 2 returns JSON. Many clients need a full restart after editing their config file.
- **Indexing returns an empty model** — `paths` must be absolute and point at a project root containing `.java` sources (or a Maven/Gradle module). Relative paths are resolved against the server's working directory, which is usually not what you want.
- **Stale results after editing source** — re-run `index_workspace` to rebuild the cached model.

## Upgrading

Pull the latest changes and rebuild:

```sh
git pull
mvn clean package
```

The jar is built with the stable name `spoon-mcp-server.jar`; rebuild it after pulling changes.
