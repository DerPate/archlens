package dev.dominikbreu.spoonmcp.model.ids;

/**
 * Reference to a shared-state field on a specific component.
 * Both fields are non-null by construction — use {@link FieldBinding.CrossComponent}
 * to represent a cross-component field read; use {@link FieldBinding.Own} for reads
 * of a field on {@code this}.
 */
public record FieldRef(ComponentId owner, String fieldName) {}
