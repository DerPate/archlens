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
        String log = String.join("\n", state.systemLog());
        DashboardEvent event = state.currentEvent();
        String result = event == null
                ? "No command executed yet."
                : (event.isError() ? "ERROR: " + event.errorText() : event.resultText());
        int leftWidth = area.width() >= 90 ? area.width() / 2 : area.width();
        Paragraph.from(log).render(new Rect(0, main.y(), leftWidth, main.height()), frame.buffer());
        Paragraph.builder()
                .text(result == null ? "" : result)
                .scroll(ui.scrollOffset())
                .build()
                .render(new Rect(leftWidth, main.y(), area.width() - leftWidth, main.height()), frame.buffer());
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
}
