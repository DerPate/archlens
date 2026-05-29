package dev.dominikbreu.spoonmcp.extractor.sourcefacts;

import dev.dominikbreu.spoonmcp.model.ids.SourceFactId;
import java.util.List;

public record SourceMethod(
        SourceFactId id,
        SourceFactId typeId,
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
