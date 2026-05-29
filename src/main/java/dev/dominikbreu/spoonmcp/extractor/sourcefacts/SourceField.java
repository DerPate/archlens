package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import dev.dominikbreu.spoonmcp.model.ids.SourceFactId;

public record SourceField(
        SourceFactId id, SourceFactId typeId, String name, String fieldType, SourceLocation location) {}
