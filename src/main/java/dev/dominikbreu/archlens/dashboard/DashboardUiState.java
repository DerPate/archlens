package dev.dominikbreu.archlens.dashboard;

import dev.tamboui.widgets.input.TextInputState;
import java.util.List;

/** Mutable interaction state owned by the dashboard view. */
final class DashboardUiState {
    private final TextInputState input = new TextInputState();
    private int focus;
    private int historyIndex = -1;
    private int scrollOffset;

    TextInputState input() { return input; }
    int focus() { return focus; }
    void cycleFocus() { focus = (focus + 1) % 3; }
    void submit() { historyIndex = -1; scrollOffset = 0; }
    void previousCommand(List<String> history) {
        if (history.isEmpty()) return;
        historyIndex = Math.min(history.size() - 1, historyIndex < 0 ? 0 : historyIndex + 1);
        input.setText(history.get(history.size() - 1 - historyIndex)); input.moveCursorToEnd();
    }
    void nextCommand(List<String> history) {
        if (historyIndex < 0) return;
        historyIndex--;
        if (historyIndex < 0) input.clear(); else { input.setText(history.get(history.size() - 1 - historyIndex)); input.moveCursorToEnd(); }
    }
    void complete(List<String> candidates) {
        String prefix = input.text();
        candidates.stream().filter(c -> c.startsWith(prefix)).findFirst().ifPresent(value -> { input.setText(value); input.moveCursorToEnd(); });
    }
    void scrollUp() { scrollOffset = Math.max(0, scrollOffset - 1); }
    void scrollDown() { scrollOffset++; }
    void home() { scrollOffset = 0; }
    int scrollOffset() { return scrollOffset; }
}
