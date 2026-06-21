package dev.dominikbreu.archlens.dashboard;

/** Outcome of {@link ReplEngine#dispatch}: either an event to display, or a request to quit. */
public record DispatchResult(DashboardEvent event, boolean quit) {}
