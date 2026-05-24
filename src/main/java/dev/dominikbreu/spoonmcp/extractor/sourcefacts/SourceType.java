package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

public record SourceType(
        String id,
        String qualifiedName,
        String simpleName,
        String packageName,
        boolean interfaceType,
        boolean abstractType,
        SourceLocation location) {}
