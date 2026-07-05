package dev.dominikbreu.archlens.dashboard;

import java.util.ArrayList;
import java.util.List;

/** Mutable view-model for the dashboard: a short system-log history plus the latest command's event. */
public final class DashboardState {

    private static final int MAX_LOG_LINES = 5;

    private final List<String> systemLog = new ArrayList<>();
    private DashboardEvent currentEvent;

    /** Creates an empty dashboard state with no events or log history. */
    public DashboardState() {}

    /**
     * Appends a message to the system log, maintaining a fixed history size.
     *
     * @param message the message to log
     */
    public void logSystemMessage(String message) {
        systemLog.add(message);
        while (systemLog.size() > MAX_LOG_LINES) {
            systemLog.remove(0);
        }
    }

    /**
     * Records the latest command event.
     *
     * @param event the event to record
     */
    public void recordEvent(DashboardEvent event) {
        this.currentEvent = event;
    }

    /**
     * Returns an immutable copy of the system log.
     *
     * @return the system log messages
     */
    public List<String> systemLog() {
        return List.copyOf(systemLog);
    }

    /**
     * Returns the latest command event, or null if idle.
     *
     * @return the current event, or null if no command has executed
     */
    public DashboardEvent currentEvent() {
        return currentEvent;
    }

    /**
     * Returns true if no command has been executed yet.
     *
     * @return true if the state is idle, false otherwise
     */
    public boolean isIdle() {
        return currentEvent == null;
    }
}
