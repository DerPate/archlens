package dev.dominikbreu.archlens.dashboard;

import dev.tamboui.backend.panama.PanamaBackend;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.Event;
import io.modelcontextprotocol.server.McpServerFeatures;
import java.io.IOException;
import java.util.List;

/**
 * Standalone terminal dashboard + REPL, started instead of the stdio MCP server when the process
 * is launched directly in an interactive terminal (see {@code Main}). The human typing commands
 * here is the only source of activity — there is no second, concurrent agent connection.
 */
public final class Dashboard {

    private final ReplEngine engine;
    private final DashboardState state = new DashboardState();

    /**
     * Initializes the dashboard with available MCP tool specifications and server version.
     *
     * @param tools list of available tool specifications
     * @param serverVersion the server version string
     */
    public Dashboard(List<McpServerFeatures.SyncToolSpecification> tools, String serverVersion) {
        this.engine = new ReplEngine(tools);
        state.logSystemMessage("archlens " + serverVersion + " — standalone dashboard");
        state.logSystemMessage("No workspace indexed yet — try: index_workspace paths=[\"./your-repo\"]");
    }

    /**
     * Runs the interactive terminal dashboard and REPL loop.
     *
     * @throws IOException if terminal initialization fails
     */
    public void run() throws IOException {
        TambouiDashboardView view = new TambouiDashboardView(state, commandNames());
        try (TuiRunner runner = TuiRunner.create(TuiConfig.builder().backend(new PanamaBackend())
                .mouseCapture(false).bracketedPaste(true).noTick().build())) {
            runner.run((event, ignored) -> handle(event, view, runner), view::render);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Unable to run dashboard", e);
        }
    }

    private boolean handle(Event event, TambouiDashboardView view, TuiRunner runner) {
        DashboardAction action = view.handle(event);
        if (action == DashboardAction.QUIT) {
            runner.quit();
            return false;
        }
        if (action != DashboardAction.SUBMIT || view.command().isBlank()) {
            return true;
        }
        view.setBusy(true);
        try {
            DispatchResult result = engine.dispatch(view.command());
            if (result.quit()) runner.quit();
            else {
                state.recordEvent(result.event());
                view.commandCompleted();
            }
        } finally {
            view.setBusy(false);
        }
        return true;
    }

    private List<String> commandNames() {
        return engine.tools().stream().map(spec -> spec.tool().name()).toList();
    }
}
