package dev.dominikbreu.archlens.dashboard;

import java.util.ArrayList;
import java.util.List;

/** Renders a {@link DashboardState} into the two-pane terminal layout, given a target width. */
public final class DashboardRenderer {

    private static final int MIN_WIDTH = 60;

    private DashboardRenderer() {}

    public static String render(DashboardState state, int terminalWidth) {
        int width = Math.max(terminalWidth, MIN_WIDTH);
        int paneWidth = (width - 3) / 2;

        StringBuilder sb = new StringBuilder();
        sb.append("ArchLens — standalone dashboard\n");
        for (String line : state.systemLog()) {
            sb.append(line).append('\n');
        }
        sb.append("-".repeat(width)).append('\n');

        List<String> left;
        List<String> right;
        if (state.isIdle()) {
            left = List.of("(idle — type a command, or :help)");
            right = List.of("(idle — type a command, or :help)");
        } else {
            left = leftPaneLines(state.currentEvent());
            right = rightPaneLines(state.currentEvent());
        }

        sb.append(pad("TRAVERSAL TRACE", paneWidth))
                .append(" | ")
                .append("COMMAND + RESULT")
                .append('\n');
        int rows = Math.max(left.size(), right.size());
        for (int i = 0; i < rows; i++) {
            String l = i < left.size() ? left.get(i) : "";
            String r = i < right.size() ? right.get(i) : "";
            sb.append(pad(truncate(l, paneWidth), paneWidth))
                    .append(" | ")
                    .append(truncate(r, paneWidth))
                    .append('\n');
        }
        return sb.toString();
    }

    private static List<String> leftPaneLines(DashboardEvent event) {
        if (event.traversalTraces().isEmpty()) {
            return List.of("(no Gremlin traversal for this command)");
        }
        return event.traversalTraces();
    }

    private static List<String> rightPaneLines(DashboardEvent event) {
        if (event.isError()) {
            return List.of("> " + event.commandLine(), "ERROR: " + event.errorText());
        }
        List<String> lines = new ArrayList<>();
        lines.add("> " + event.commandLine() + " (" + event.durationMillis() + "ms)");
        for (String resultLine : event.resultText().split("\n", -1)) {
            lines.add(resultLine);
        }
        return lines;
    }

    private static String pad(String text, int width) {
        String truncated = truncate(text, width);
        return truncated + " ".repeat(width - truncated.length());
    }

    private static String truncate(String text, int width) {
        return text.length() <= width ? text : text.substring(0, width - 1) + "…";
    }
}
