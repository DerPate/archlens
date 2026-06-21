package dev.dominikbreu.archlens.extractor.sourcefacts;

import dev.dominikbreu.archlens.model.ids.SourceFactId;
import java.util.List;

/**
 * A source-level method or constructor declaration observed on a type.
 *
 * @param id the fact id for this method
 * @param typeId the fact id of the declaring type
 * @param name the simple method name
 * @param signature the method signature ({@code name(TypeA,TypeB)})
 * @param constructor true if this is a constructor declaration
 * @param parameterNames the ordered parameter names
 * @param parameterTypes the ordered parameter type names
 * @param location the source location of the method declaration
 */
public record SourceMethod(
        SourceFactId id,
        SourceFactId typeId,
        String name,
        String signature,
        boolean constructor,
        List<String> parameterNames,
        List<String> parameterTypes,
        SourceLocation location) {

    /** Defensively copies the parameter lists. */
    public SourceMethod {
        parameterNames = List.copyOf(parameterNames);
        parameterTypes = List.copyOf(parameterTypes);
    }
}
