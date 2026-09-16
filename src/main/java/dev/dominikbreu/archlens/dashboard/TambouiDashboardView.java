package dev.dominikbreu.archlens.dashboard;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.PasteEvent;
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.paragraph.Paragraph;
import java.util.List;

/** TamboUI-rendered dashboard and keyboard interaction model. */
final class TambouiDashboardView {
    private final DashboardState state;
    private final List<String> commands;
    private final DashboardUiState ui = new DashboardUiState();
    private boolean busy;

    TambouiDashboardView(DashboardState state, List<String> commands) {
        this.state = state;
        this.commands = commands;
    }

    DashboardAction handle(Event event) {
        if (event instanceof PasteEvent paste) { ui.input().insert(paste.text()); return DashboardAction.NONE; }
        if (!(event instanceof KeyEvent key)) return DashboardAction.NONE;
        if (key.isCtrlC()) return DashboardAction.QUIT;
        if (key.isKey(KeyCode.F6)) { ui.cycleFocus(); return DashboardAction.NONE; }
        if (key.isKey(KeyCode.ENTER)) return DashboardAction.SUBMIT;
        if (key.isKey(KeyCode.TAB)) { ui.complete(commands); return DashboardAction.NONE; }
        if (key.isUp()) { ui.previousCommand(commands); return DashboardAction.NONE; }
        if (key.isDown()) { ui.nextCommand(commands); return DashboardAction.NONE; }
        if (key.isPageUp()) { ui.scrollUp(); return DashboardAction.NONE; }
        if (key.isPageDown()) { ui.scrollDown(); return DashboardAction.NONE; }
        if (key.isHome()) { ui.input().moveCursorToStart(); return DashboardAction.NONE; }
        if (key.isEnd()) { ui.input().moveCursorToEnd(); return DashboardAction.NONE; }
        if (key.isDeleteBackward()) { ui.input().deleteBackward(); return DashboardAction.NONE; }
        if (key.isDeleteForward()) { ui.input().deleteForward(); return DashboardAction.NONE; }
        if (key.isLeft()) { ui.input().moveCursorLeft(); return DashboardAction.NONE; }
        if (key.isRight()) { ui.input().moveCursorRight(); return DashboardAction.NONE; }
        if (key.code() == KeyCode.CHAR) ui.input().insert(key.character());
        return DashboardAction.NONE;
    }

    String command() { return ui.input().text(); }
    void commandCompleted() { ui.submit(); ui.input().clear(); }
    void setBusy(boolean busy) { this.busy = busy; }

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
        String result = event == null ? "No command executed yet." : (event.isError() ? "ERROR: " + event.errorText() : event.resultText());
        int leftWidth = area.width() >= 90 ? area.width() / 2 : area.width();
        Paragraph.from(log).render(new Rect(0, main.y(), leftWidth, main.height()), frame.buffer());
        Paragraph.builder().text(result == null ? "" : result).scroll(ui.scrollOffset()).build()
                .render(new Rect(leftWidth, main.y(), area.width() - leftWidth, main.height()), frame.buffer());
        TextInput.builder().placeholder("Enter an MCP command (Tab completes, F6 focus, Ctrl-C quits)").build()
                .renderWithCursor(new Rect(0, inputY, area.width(), 1), frame.buffer(), ui.input(), frame);
        Paragraph.from(busy ? "Running…" : "Ready").render(new Rect(0, inputY + 1, area.width(), 1), frame.buffer());
    }
}
