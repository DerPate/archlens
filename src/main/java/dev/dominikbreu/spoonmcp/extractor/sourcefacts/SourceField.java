package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

public record SourceField(
        String id,
        String typeId,
        String name,
        String fieldType,
        SourceLocation location) {}
