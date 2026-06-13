package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import dev.dominikbreu.spoonmcp.model.ids.SourceFactId;

/**
 * A source-level type declaration (class, interface, enum, or annotation type).
 *
 * @param id the fact id for this type
 * @param qualifiedName the fully-qualified type name
 * @param simpleName the simple type name
 * @param packageName the package name
 * @param interfaceType true if this type is an interface
 * @param abstractType true if this type is abstract
 * @param location the source location of the type declaration
 */
public record SourceType(
        SourceFactId id,
        String qualifiedName,
        String simpleName,
        String packageName,
        boolean interfaceType,
        boolean abstractType,
        SourceLocation location) {}
