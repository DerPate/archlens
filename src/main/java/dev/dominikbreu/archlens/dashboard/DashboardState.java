package dev.dominikbreu.archlens.dashboard;

import java.util.ArrayList;
import java.util.List;

/** Mutable view-model for the dashboard: a short system-log history plus the latest command's event. */
public final class DashboardState {

    private static final int MAX_LOG_LINES = 5;

    private final List<String> systemLog = new ArrayList<>();
    private DashboardEvent currentEvent;

    public void logSystemMessage(String message) {
        systemLog.add(message);
        while (systemLog.size() > MAX_LOG_LINES) {
            systemLog.remove(0);
        }
    }

    public void recordEvent(DashboardEvent event) {
        this.currentEvent = event;
    }

    public List<String> systemLog() {
        return List.copyOf(systemLog);
    }

    public DashboardEvent currentEvent() {
        return currentEvent;
    }

    public boolean isIdle() {
        return currentEvent == null;
    }
}
