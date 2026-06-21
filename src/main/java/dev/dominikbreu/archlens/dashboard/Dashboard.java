package dev.dominikbreu.archlens.dashboard;

import io.modelcontextprotocol.server.McpServerFeatures;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;

/**
 * Standalone terminal dashboard + REPL, started instead of the stdio MCP server when the process
 * is launched directly in an interactive terminal (see {@code Main}). The human typing commands
 * here is the only source of activity — there is no second, concurrent agent connection.
 */
public final class Dashboard {

    private final ReplEngine engine;
    private final DashboardState state = new DashboardState();

    public Dashboard(List<McpServerFeatures.SyncToolSpecification> tools, String serverVersion) {
        this.engine = new ReplEngine(tools);
        state.logSystemMessage("archlens " + serverVersion + " — standalone dashboard");
        state.logSystemMessage("No workspace indexed yet — try: index_workspace paths=[\"./your-repo\"]");
    }

    public void run() throws IOException {
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            terminal.puts(InfoCmp.Capability.enter_ca_mode);
            terminal.flush();
            try {
                runLoop(terminal);
            } finally {
                terminal.puts(InfoCmp.Capability.exit_ca_mode);
                terminal.flush();
            }
        }
    }

    private void runLoop(Terminal terminal) {
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(toolNameCompleter())
                .build();

        redraw(terminal);
        while (true) {
            String line;
            try {
                line = reader.readLine("spoon> ");
            } catch (UserInterruptException | EndOfFileException e) {
                return;
            }
            if (line.isBlank()) {
                continue;
            }

            DispatchResult result = engine.dispatch(line);
            if (result.quit()) {
                return;
            }
            if ("index_workspace".equals(result.event().toolName())
                    && !result.event().isError()) {
                state.logSystemMessage(firstLine(result.event().resultText()));
            }
            state.recordEvent(result.event());
            redraw(terminal);
        }
    }

    private void redraw(Terminal terminal) {
        terminal.puts(InfoCmp.Capability.clear_screen);
        terminal.writer().print(DashboardRenderer.render(state, terminal.getWidth()));
        terminal.flush();
    }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline);
    }

    private Completer toolNameCompleter() {
        List<String> names = new ArrayList<>();
        for (McpServerFeatures.SyncToolSpecification spec : engine.tools()) {
            names.add(spec.tool().name());
        }
        names.add(":help");
        names.add(":tools");
        names.add(":quit");

        return (LineReader lineReader, ParsedLine parsedLine, List<Candidate> candidates) -> {
            if (parsedLine.wordIndex() == 0) {
                for (String name : names) {
                    candidates.add(new Candidate(name));
                }
            }
        };
    }
}
