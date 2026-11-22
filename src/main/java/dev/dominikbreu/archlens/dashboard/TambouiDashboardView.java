package dev.dominikbreu.archlens.dashboard;

import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.PasteEvent;
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.paragraph.Paragraph;
import java.util.ArrayList;
import java.util.List;

/** TamboUI-rendered dashboard and keyboard interaction model. */
final class TambouiDashboardView {
    /** Width from which the activity and the result sit side by side. */
    private static final int WIDE = 90;

    /** Lines of the system log shown when the panes are stacked. */
    private static final int STACKED_LOG_LINES = 3;

    /** Characters shown of one traversal, which can embed thousands of ids. */
    private static final int MAX_TRACE_CHARS = 160;

    private final DashboardState state;
    private final List<String> commands;
    private final List<String> history = new ArrayList<>();
    private final DashboardUiState ui = new DashboardUiState();
    private boolean busy;

    TambouiDashboardView(DashboardState state, List<String> commands) {
        this.state = state;
        this.commands = commands;
    }

    DashboardAction handle(Event event) {
        return switch (event) {
            case PasteEvent paste -> {
                ui.input().insert(paste.text());
                yield DashboardAction.NONE;
            }
            case KeyEvent key -> handleKey(key);
            default -> DashboardAction.NONE;
        };
    }

    private DashboardAction handleKey(KeyEvent key) {
        if (key.isCtrlC()) {
            return DashboardAction.QUIT;
        }
        return switch (key.code()) {
            case F6 -> {
                ui.cycleFocus();
                yield DashboardAction.NONE;
            }
            case ENTER -> DashboardAction.SUBMIT;
            case TAB -> {
                ui.complete(commands);
                yield DashboardAction.NONE;
            }
            case UP -> {
                ui.previousCommand(history);
                yield DashboardAction.NONE;
            }
            case DOWN -> {
                ui.nextCommand(history);
                yield DashboardAction.NONE;
            }
            case PAGE_UP -> {
                ui.scrollUp();
                yield DashboardAction.NONE;
            }
            case PAGE_DOWN -> {
                ui.scrollDown();
                yield DashboardAction.NONE;
            }
            case HOME -> {
                ui.input().moveCursorToStart();
                yield DashboardAction.NONE;
            }
            case END -> {
                ui.input().moveCursorToEnd();
                yield DashboardAction.NONE;
            }
            case BACKSPACE -> {
                ui.input().deleteBackward();
                yield DashboardAction.NONE;
            }
            case DELETE -> {
                ui.input().deleteForward();
                yield DashboardAction.NONE;
            }
            case LEFT -> {
                ui.input().moveCursorLeft();
                yield DashboardAction.NONE;
            }
            case RIGHT -> {
                ui.input().moveCursorRight();
                yield DashboardAction.NONE;
            }
            case CHAR -> {
                ui.input().insert(key.character());
                yield DashboardAction.NONE;
            }
            default -> DashboardAction.NONE;
        };
    }

    String command() {
        return ui.input().text();
    }

    void rememberCommand(String command) {
        history.add(command);
    }

    void commandCompleted() {
        ui.submit();
        ui.input().clear();
    }

    void setBusy(boolean busy) {
        this.busy = busy;
    }

    void render(Frame frame) {
        Rect area = frame.area();
        if (area.width() < 40 || area.height() < 8) {
            Paragraph.from("Terminal too small — resize to at least 40×8").render(area, frame.buffer());
            return;
        }
        Paragraph.from("ARCHLENS  •  architecture dashboard").render(new Rect(0, 0, area.width(), 1), frame.buffer());
        int inputY = area.height() - 3;
        Rect main = new Rect(0, 1, area.width(), inputY - 1);
        List<String> log = state.systemLog();
        DashboardEvent event = state.currentEvent();
        String result = event == null
                ? "No command executed yet."
                : (event.isError() ? "ERROR: " + event.errorText() : event.resultText());
        String text = result == null ? "" : result;
        String traversals = traversals(event);
        Rect activityPane;
        Rect resultPane;
        String activity;
        if (area.width() >= WIDE) {
            int left = area.width() / 2;
            activityPane = new Rect(0, main.y(), left, main.height());
            resultPane = new Rect(left, main.y(), area.width() - left, main.height());
            activity = String.join("\n", log) + traversals;
        } else {
            int top = Math.min(Math.min(log.size(), STACKED_LOG_LINES), main.height() / 3);
            activityPane = new Rect(0, main.y(), area.width(), top);
            resultPane = new Rect(0, main.y() + top, area.width(), main.height() - top);
            activity = String.join("\n", log.subList(log.size() - top, log.size()));
            text += traversals; // stacked, they scroll with the result
        }
        Paragraph.from(activity).render(activityPane, frame.buffer());
        Paragraph.builder().text(text).scroll(ui.scrollOffset()).build().render(resultPane, frame.buffer());
        TextInput.builder()
                .placeholder("Enter an MCP command (Tab completes, F6 focus, Ctrl-C quits)")
                .build()
                .renderWithCursor(new Rect(0, inputY, area.width(), 1), frame.buffer(), ui.input(), frame);
        String focus =
                switch (ui.focus()) {
                    case 0 -> "input";
                    case 1 -> "activity";
                    default -> "result";
                };
        Paragraph.from((busy ? "Running…" : "Ready") + "  •  Focus: " + focus + "  •  F6 cycle focus")
                .render(new Rect(0, inputY + 1, area.width(), 1), frame.buffer());
    }

    /** Formats the traversals the latest command ran, or nothing if it recorded none. */
    private static String traversals(DashboardEvent event) {
        if (event == null || event.traversalTraces().isEmpty()) return "";
        StringBuilder text = new StringBuilder("\n\nTraversals (")
                .append(event.traversalTraces().size())
                .append(", ")
                .append(event.durationMillis())
                .append(" ms):");
        for (String trace : event.traversalTraces()) {
            text.append("\n")
                    .append(trace.length() > MAX_TRACE_CHARS ? trace.substring(0, MAX_TRACE_CHARS) + "…" : trace);
        }
        return text.toString();
    }
}
