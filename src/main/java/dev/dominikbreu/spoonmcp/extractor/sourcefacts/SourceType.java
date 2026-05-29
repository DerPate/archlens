package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import dev.dominikbreu.spoonmcp.model.ids.SourceFactId;

public record SourceType(
        SourceFactId id,
        String qualifiedName,
        String simpleName,
        String packageName,
        boolean interfaceType,
        boolean abstractType,
        SourceLocation location) {}
