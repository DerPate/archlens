package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import java.util.List;

public record SourceMethod(
        String id,
        String typeId,
        String name,
        String signature,
        boolean constructor,
        List<String> parameterNames,
        List<String> parameterTypes,
        SourceLocation location) {

    public SourceMethod {
        parameterNames = List.copyOf(parameterNames);
        parameterTypes = List.copyOf(parameterTypes);
    }
}
