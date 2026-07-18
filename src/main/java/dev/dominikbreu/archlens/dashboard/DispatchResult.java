package dev.dominikbreu.archlens.dashboard;

/**
 * Outcome of {@link ReplEngine#dispatch}: either an event to display, or a request to quit.
 *
 * @param event the event to render, or {@code null} when quitting
 * @param quit {@code true} if the REPL should exit
 */
public record DispatchResult(DashboardEvent event, boolean quit) {}
